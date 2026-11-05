package org.protege.ai.core.plugin;

import org.protege.ai.api.AgentTool;
import org.protege.ai.api.AgentToolSource;
import org.protege.editor.core.plugin.ProtegePlugin;

/** A plugin descriptor, not an instantiated tool. */
public interface AgentToolPlugin extends AgentToolSource, ProtegePlugin<AgentTool> {
    String PLUGIN_ID = "org.protege.ai.core";
    String EXTENSION_POINT_ID = "AgentTool";
}
