package org.protege.ai.api;

import org.protege.editor.core.plugin.ProtegePluginInstance;

/** A tool instance owned by one ontology window. */
public interface AgentTool extends ProtegePluginInstance {

    /**
     * Runs synchronously and may be called concurrently, including on the same instance.
     * Input and successful output follow the tool's declared formats.
     * Exception messages are returned to the caller and should explain the failure for the model.
     */
    String execute(String input) throws Exception;

    @Override
    default void initialise() throws Exception {
    }

    /** Releases resources promptly, after all invocations on this instance have returned. */
    @Override
    default void dispose() throws Exception {
    }
}
