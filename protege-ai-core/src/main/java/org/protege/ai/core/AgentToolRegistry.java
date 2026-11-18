package org.protege.ai.core;

import org.protege.ai.api.AgentTool;
import org.protege.ai.api.AgentToolDefinition;
import org.protege.ai.api.AgentToolException;
import org.protege.ai.api.AgentToolResult;
import org.protege.ai.api.AgentToolSchemas;
import org.protege.ai.api.AgentToolSource;
import org.protege.ai.api.ToolRegistry;
import org.protege.ai.core.plugin.AgentToolPluginLoader;
import org.protege.editor.core.Disposable;
import org.protege.editor.owl.OWLEditorKit;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.protege.ai.api.AgentToolResult.Status.*;

/** Owns the tools for one editor kit. Synchronous calls may run concurrently. */
public final class AgentToolRegistry implements ToolRegistry, Disposable {

    private static final Logger logger = LoggerFactory.getLogger(AgentToolRegistry.class);
    private static final String KEY = "org.protege.ai.core.AgentToolRegistry";
    private static final Object REGISTRY_CREATION_LOCK = new Object();

    private static final class Entry<I, O> {
        final String sourceId;
        final AgentToolDefinition<I, O> definition;
        final AgentTool<I, O> tool;
        final ToolJsonCodec<I, O> codec;
        int inFlight;

        Entry(String sourceId, AgentToolDefinition<I, O> definition, AgentTool<I, O> tool,
              ToolJsonCodec<I, O> codec) {
            this.sourceId = sourceId;
            this.definition = definition;
            this.tool = tool;
            this.codec = codec;
        }
    }

    private final Map<String, Entry<?, ?>> tools = new LinkedHashMap<>();
    private final Map<String, String> claimedNames = new LinkedHashMap<>();
    private final OWLDataFactory dataFactory;
    private boolean disposed;

    public AgentToolRegistry(Iterable<? extends AgentToolSource<?, ?>> sources) {
        this(sources, OWLManager.getOWLDataFactory());
    }

    /** Creates a registry with a supplied OWL factory, without requiring an editor kit. */
    public AgentToolRegistry(Iterable<? extends AgentToolSource<?, ?>> sources, OWLDataFactory dataFactory) {
        this.dataFactory = Objects.requireNonNull(dataFactory);
        sources.forEach(this::register);
    }

    /**
     * Returns the window's registry, creating it if necessary.
     * Call on the Swing thread, normally from an editor-kit hook's {@code initialise()},
     * and keep the returned reference for later use.
     * The editor kit forwards put/get to the model manager's unsynchronized map,
     * which Protégé writes on the Swing thread.
     */
    public static AgentToolRegistry getRegistry(OWLEditorKit editorKit) {
        Objects.requireNonNull(editorKit);
        synchronized (REGISTRY_CREATION_LOCK) {
            Disposable existing = editorKit.get(KEY);
            if (existing instanceof AgentToolRegistry registry) {
                return registry;
            }
            AgentToolRegistry registry = new AgentToolRegistry(List.of(),
                    editorKit.getOWLModelManager().getOWLDataFactory());
            editorKit.put(KEY, registry);
            new AgentToolPluginLoader(editorKit).getPlugins().stream()
                    .sorted(java.util.Comparator.comparing(AgentToolSource::getId))
                    .forEach(registry::register);
            return registry;
        }
    }

    /** Rejects duplicate names before constructing the second tool. */
    public synchronized <I, O> boolean register(AgentToolSource<I, O> source) {
        if (disposed) {
            throw new IllegalStateException("Tool registry is closed");
        }
        AgentTool<I, O> tool = null;
        String sourceId = "<unknown>";
        try {
            sourceId = Objects.requireNonNull(source.getId());
            AgentToolDefinition<I, O> definition = Objects.requireNonNull(source.getDefinition());
            String previous = claimedNames.putIfAbsent(definition.name(), sourceId);
            if (previous != null) {
                logger.warn("Rejecting tool '{}' from '{}': already claimed by '{}'",
                        definition.name(), sourceId, previous);
                return false;
            }
            ToolJsonCodec<I, O> codec = new ToolJsonCodec<>(definition, dataFactory);
            tool = Objects.requireNonNull(source.newInstance(), "Tool factory returned null");
            if (!definition.equals(tool.getDefinition())) {
                throw new IllegalArgumentException("Tool definition does not match its factory's definition");
            }
            tool.initialise();
            tools.put(definition.name(), new Entry<>(sourceId, definition, tool, codec));
            return true;
        } catch (Exception | LinkageError e) {
            logger.warn("Could not load tool from '{}'", sourceId, e);
            if (tool != null) {
                disposeTool(tool, sourceId);
            }
            return false;
        }
    }

    @Override
    public synchronized List<AgentToolDefinition<?, ?>> listTools() {
        return tools.values().stream().<AgentToolDefinition<?, ?>>map(entry -> entry.definition).toList();
    }

    @Override
    public synchronized Optional<AgentToolDefinition<?, ?>> findTool(String name) {
        return Optional.ofNullable(tools.get(name)).map(entry -> entry.definition);
    }

    @Override
    public synchronized Optional<AgentToolSchemas> findSchemas(String name) {
        return Optional.ofNullable(tools.get(name)).map(entry -> entry.codec.schemas());
    }

    /** Validates and invokes on the caller's thread, without timeouts, cancellation or rollback. */
    @Override
    public AgentToolResult execute(String name, String input) {
        if (input == null) {
            return new AgentToolResult.Failure(INVALID_INPUT, "Tool input must not be null");
        }
        Entry<?, ?> entry;
        synchronized (this) {
            if (disposed) {
                return new AgentToolResult.Failure(REGISTRY_CLOSED, "Tool registry is closed");
            }
            entry = tools.get(name);
            if (entry == null) {
                return new AgentToolResult.Failure(NO_SUCH_TOOL, "No such tool: " + name);
            }
            entry.inFlight++;
        }
        try {
            return invoke(entry, input);
        } catch (ToolJsonCodec.InvalidInputException e) {
            return new AgentToolResult.Failure(INVALID_INPUT, e.getMessage(), INVALID_INPUT.name(), e.details());
        } catch (AgentToolException e) {
            return new AgentToolResult.Failure(TOOL_ERROR, e.getMessage(), e.code(), e.details());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new AgentToolResult.Failure(INTERRUPTED, "Tool call was interrupted: " + name);
        } catch (VirtualMachineError e) {
            throw e;
        } catch (Throwable e) {
            String reference = UUID.randomUUID().toString();
            logger.warn("Tool '{}' from '{}' failed (reference {})", name, entry.sourceId, reference, e);
            return new AgentToolResult.Failure(EXECUTION_ERROR,
                    "Tool execution failed: " + name + ". See the application log (reference " + reference + ").");
        } finally {
            boolean disposeTool;
            synchronized (this) {
                entry.inFlight--;
                disposeTool = disposed && entry.inFlight == 0;
            }
            if (disposeTool) {
                disposeTool(entry.tool, entry.sourceId);
            }
        }
    }

    /** Does not wait for running calls; each busy tool is disposed by its last returning call. */
    @Override
    public void dispose() {
        List<Entry<?, ?>> idle = new ArrayList<>();
        synchronized (this) {
            if (disposed) {
                return;
            }
            disposed = true;
            for (Entry<?, ?> entry : tools.values()) {
                if (entry.inFlight == 0) {
                    idle.add(entry);
                }
            }
            tools.clear();
            claimedNames.clear();
        }
        for (Entry<?, ?> entry : idle.reversed()) {
            disposeTool(entry.tool, entry.sourceId);
        }
    }

    private static <I, O> AgentToolResult invoke(Entry<I, O> entry, String input) throws Exception {
        I arguments = entry.codec.readArguments(input);
        O result = entry.tool.execute(arguments);
        String json = entry.codec.writeResult(result);
        return new AgentToolResult.Success<>(result, json);
    }

    private static void disposeTool(AgentTool<?, ?> tool, String sourceId) {
        try {
            tool.dispose();
        } catch (Exception | LinkageError e) {
            logger.warn("Could not dispose tool from '{}'", sourceId, e);
        }
    }
}
