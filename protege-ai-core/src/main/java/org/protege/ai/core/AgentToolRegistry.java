package org.protege.ai.core;

import org.protege.ai.api.AgentTool;
import org.protege.ai.api.AgentToolDescription;
import org.protege.ai.api.AgentToolResult;
import org.protege.ai.api.AgentToolSource;
import org.protege.ai.api.ToolRegistry;
import org.protege.ai.core.plugin.AgentToolPluginLoader;
import org.protege.editor.core.Disposable;
import org.protege.editor.owl.OWLEditorKit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static org.protege.ai.api.AgentToolResult.Status.*;

/** Owns the tools for one editor kit. Synchronous calls may run concurrently. */
public final class AgentToolRegistry implements ToolRegistry, Disposable {

    private static final Logger logger = LoggerFactory.getLogger(AgentToolRegistry.class);
    private static final String KEY = "org.protege.ai.core.AgentToolRegistry";
    private static final Object REGISTRY_CREATION_LOCK = new Object();

    private static final class Entry {
        final String sourceId;
        final AgentToolDescription description;
        final AgentTool tool;
        int inFlight;

        Entry(String sourceId, AgentToolDescription description, AgentTool tool) {
            this.sourceId = sourceId;
            this.description = description;
            this.tool = tool;
        }
    }

    private final Map<String, Entry> tools = new LinkedHashMap<>();
    private final Map<String, String> claimedNames = new LinkedHashMap<>();
    private boolean disposed;

    public AgentToolRegistry(Iterable<? extends AgentToolSource> sources) {
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
            AgentToolRegistry registry = new AgentToolRegistry(List.of());
            editorKit.put(KEY, registry);
            new AgentToolPluginLoader(editorKit).getPlugins().stream()
                    .sorted(java.util.Comparator.comparing(AgentToolSource::getId))
                    .forEach(registry::register);
            return registry;
        }
    }

    /** Rejects duplicate names before constructing the second tool. */
    public synchronized boolean register(AgentToolSource source) {
        if (disposed) {
            throw new IllegalStateException("Tool registry is closed");
        }
        AgentTool tool = null;
        String sourceId = "<unknown>";
        try {
            sourceId = Objects.requireNonNull(source.getId());
            AgentToolDescription description = Objects.requireNonNull(source.getDescription());
            String previous = claimedNames.putIfAbsent(description.name(), sourceId);
            if (previous != null) {
                logger.warn("Rejecting tool '{}' from '{}': already claimed by '{}'",
                        description.name(), sourceId, previous);
                return false;
            }
            tool = Objects.requireNonNull(source.newInstance(), "Tool factory returned null");
            tool.initialise();
            tools.put(description.name(), new Entry(sourceId, description, tool));
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
    public synchronized List<AgentToolDescription> listTools() {
        return tools.values().stream().map(entry -> entry.description).toList();
    }

    @Override
    public synchronized Optional<AgentToolDescription> findTool(String name) {
        return Optional.ofNullable(tools.get(name)).map(entry -> entry.description);
    }

    /** No worker, schema validation, timeout, cancellation or rollback is implied by this call. */
    @Override
    public AgentToolResult execute(String name, String input) {
        if (input == null) {
            return new AgentToolResult(INVALID_INPUT, "Tool input must not be null");
        }
        Entry entry;
        synchronized (this) {
            if (disposed) {
                return new AgentToolResult(REGISTRY_CLOSED, "Tool registry is closed");
            }
            entry = tools.get(name);
            if (entry == null) {
                return new AgentToolResult(NO_SUCH_TOOL, "No such tool: " + name);
            }
            entry.inFlight++;
        }
        try {
            return new AgentToolResult(SUCCESS, entry.tool.execute(input));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Tool '{}' from '{}' interrupted", name, entry.sourceId, e);
            return executionError("Tool interrupted: " + name, e);
        } catch (VirtualMachineError e) {
            throw e;
        } catch (Throwable e) {
            logger.warn("Tool '{}' from '{}' failed", name, entry.sourceId, e);
            return executionError("Tool execution failed: " + name, e);
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
        List<Entry> idle = new ArrayList<>();
        synchronized (this) {
            if (disposed) {
                return;
            }
            disposed = true;
            for (Entry entry : tools.values()) {
                if (entry.inFlight == 0) {
                    idle.add(entry);
                }
            }
            tools.clear();
            claimedNames.clear();
        }
        for (Entry entry : idle.reversed()) {
            disposeTool(entry.tool, entry.sourceId);
        }
    }

    private static AgentToolResult executionError(String summary, Throwable cause) {
        String message = cause.getMessage();
        String detail = message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
        return new AgentToolResult(EXECUTION_ERROR, summary + ": " + detail);
    }

    private static void disposeTool(AgentTool tool, String sourceId) {
        try {
            tool.dispose();
        } catch (Exception | LinkageError e) {
            logger.warn("Could not dispose tool from '{}'", sourceId, e);
        }
    }
}
