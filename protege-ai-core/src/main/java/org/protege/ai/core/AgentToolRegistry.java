package org.protege.ai.core;

import org.protege.ai.api.AgentTool;
import org.protege.ai.api.AgentToolDescription;
import org.protege.ai.api.AgentToolResult;
import org.protege.ai.api.AgentToolSource;
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

/** Owns the tools for one editor kit. Calls are synchronous and serialized per registry. */
public final class AgentToolRegistry implements Disposable {

    private static final Logger logger = LoggerFactory.getLogger(AgentToolRegistry.class);
    private static final String KEY = "org.protege.ai.core.AgentToolRegistry";

    private record Entry(String sourceId, AgentToolDescription description, AgentTool tool) {
    }

    private final Map<String, Entry> tools = new LinkedHashMap<>();
    private boolean disposed;

    public AgentToolRegistry(Iterable<? extends AgentToolSource> sources) {
        sources.forEach(this::register);
    }

    /** Also works from another editor-kit hook, whose initialization order is unspecified. */
    public static AgentToolRegistry getRegistry(OWLEditorKit editorKit) {
        Objects.requireNonNull(editorKit);
        synchronized (editorKit) {
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
            Entry previous = tools.get(description.name());
            if (previous != null) {
                logger.warn("Rejecting tool '{}' from '{}': already registered by '{}'",
                        description.name(), sourceId, previous.sourceId());
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

    public synchronized List<AgentToolDescription> listTools() {
        return tools.values().stream().map(Entry::description).toList();
    }

    public synchronized Optional<AgentToolDescription> findTool(String name) {
        return Optional.ofNullable(tools.get(name)).map(Entry::description);
    }

    /** No worker, schema validation, timeout, cancellation or rollback is implied by this call. */
    public synchronized AgentToolResult execute(String name, String input) {
        if (disposed) {
            return new AgentToolResult(REGISTRY_CLOSED, "Tool registry is closed");
        }
        Entry entry = tools.get(name);
        if (entry == null) {
            return new AgentToolResult(NO_SUCH_TOOL, "No such tool: " + name);
        }
        try {
            return new AgentToolResult(SUCCESS, entry.tool().execute(Objects.requireNonNull(input)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new AgentToolResult(EXECUTION_ERROR, "Tool interrupted: " + name);
        } catch (Exception | LinkageError e) {
            logger.warn("Tool '{}' from '{}' failed", name, entry.sourceId(), e);
            return new AgentToolResult(EXECUTION_ERROR, "Tool execution failed: " + name);
        }
    }

    @Override
    public synchronized void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        List<Entry> entries = new ArrayList<>(tools.values());
        tools.clear();
        for (Entry entry : entries.reversed()) {
            disposeTool(entry.tool(), entry.sourceId());
        }
    }

    private static void disposeTool(AgentTool tool, String sourceId) {
        try {
            tool.dispose();
        } catch (Exception | LinkageError e) {
            logger.warn("Could not dispose tool from '{}'", sourceId, e);
        }
    }
}
