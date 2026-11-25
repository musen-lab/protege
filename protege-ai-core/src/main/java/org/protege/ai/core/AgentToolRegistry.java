package org.protege.ai.core;

import org.protege.ai.api.AgentTool;
import org.protege.ai.api.AgentToolDefinition;
import org.protege.ai.api.AgentToolException;
import org.protege.ai.api.AgentToolSource;
import org.protege.ai.api.ToolRegistry;
import org.protege.ai.api.ToolFailure;
import org.protege.ai.api.ToolExecutionResult;
import org.protege.editor.core.Disposable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.protege.ai.api.ToolStatus.*;

/** Owns the tools for one editor kit. Synchronous calls may run concurrently. */
public final class AgentToolRegistry implements ToolRegistry, Disposable {

    private static final Logger logger = LoggerFactory.getLogger(AgentToolRegistry.class);

    private static final class Entry<I, O> {
        final String sourceId;
        final AgentToolDefinition<I, O> definition;
        final AgentTool<I, O> tool;
        int inFlight;

        Entry(String sourceId, AgentToolDefinition<I, O> definition, AgentTool<I, O> tool) {
            this.sourceId = sourceId;
            this.definition = definition;
            this.tool = tool;
        }
    }

    private final Map<String, Entry<?, ?>> tools = new LinkedHashMap<>();
    private final Map<String, String> claimedNames = new LinkedHashMap<>();
    private final Consumer<AgentToolDefinition<?, ?>> checkDefinition;
    private boolean disposed;

    public AgentToolRegistry(Iterable<? extends AgentToolSource<?, ?>> sources) {
        this(sources, definition -> {});
    }

    // The desktop wiring supplies its contract check without making the registry depend on a format.
    AgentToolRegistry(Iterable<? extends AgentToolSource<?, ?>> sources,
                      Consumer<AgentToolDefinition<?, ?>> checkDefinition) {
        this.checkDefinition = Objects.requireNonNull(checkDefinition);
        sources.forEach(this::register);
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
            checkDefinition.accept(definition);
            tool = Objects.requireNonNull(source.newInstance(), "Tool factory returned null");
            if (!definition.equals(tool.getDefinition())) {
                throw new IllegalArgumentException("Tool definition does not match its factory's definition");
            }
            tool.initialise();
            tools.put(definition.name(), new Entry<>(sourceId, definition, tool));
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

    /** Checks Java argument/result types and invokes synchronously. Does not apply schema rules or defaults. */
    @Override
    public ToolExecutionResult execute(String name, Object arguments) {
        return execute(name, definition -> arguments, (definition, result) -> result);
    }

    @FunctionalInterface
    interface ArgumentReader {
        Object read(AgentToolDefinition<?, ?> definition) throws Exception;
    }

    @FunctionalInterface
    interface ResultProcessor {
        Object process(AgentToolDefinition<?, ?> definition, Object result) throws Exception;
    }

    // Keep the tool in flight through input preparation and result processing, outside the registry lock.
    ToolExecutionResult execute(String name, ArgumentReader reader, ResultProcessor processor) {
        Entry<?, ?> entry;
        synchronized (this) {
            if (disposed) {
                return new ToolFailure(REGISTRY_CLOSED, "Tool registry is closed");
            }
            entry = tools.get(name);
            if (entry == null) {
                return new ToolFailure(NO_SUCH_TOOL, "No such tool: " + name);
            }
            entry.inFlight++;
        }
        try {
            Object arguments = reader.read(entry.definition);
            if (!entry.definition.argumentType().isInstance(arguments)) {
                return new ToolFailure(INVALID_INPUT, "Arguments must be a non-null "
                        + entry.definition.argumentType().getSimpleName());
            }
            Object result = invoke(entry, arguments);
            Object processed = processor.process(entry.definition, result);
            return new ToolExecutionResult.Success<>(processed);
        } catch (InvalidToolArgumentsException e) {
            return new ToolFailure(INVALID_INPUT, e.getMessage(), INVALID_INPUT.name(), e.details());
        } catch (AgentToolException e) {
            return new ToolFailure(TOOL_ERROR, e.getMessage(), e.code(), e.details());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ToolFailure(INTERRUPTED, "Tool call was interrupted: " + name);
        } catch (VirtualMachineError e) {
            throw e;
        } catch (Throwable e) {
            String reference = UUID.randomUUID().toString();
            logger.warn("Tool '{}' from '{}' failed (reference {})", name, entry.sourceId, reference, e);
            return new ToolFailure(EXECUTION_ERROR,
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

    private static <I, O> O invoke(Entry<I, O> entry, Object arguments) throws Exception {
        I input = entry.definition.argumentType().cast(arguments);
        O result = entry.tool.execute(input);
        return entry.definition.resultType().cast(Objects.requireNonNull(result, "Tool returned null"));
    }

    private static void disposeTool(AgentTool<?, ?> tool, String sourceId) {
        try {
            tool.dispose();
        } catch (Exception | LinkageError e) {
            logger.warn("Could not dispose tool from '{}'", sourceId, e);
        }
    }
}
