package org.protege.ai.api;

import org.protege.editor.core.plugin.ProtegePluginInstance;

/** A tool instance owned by one ontology window. */
public interface AgentTool extends ProtegePluginInstance {

    /** Runs synchronously. Input and successful output follow the tool's declared formats. */
    String execute(String input) throws Exception;

    @Override
    default void initialise() throws Exception {
    }

    @Override
    default void dispose() throws Exception {
    }
}
