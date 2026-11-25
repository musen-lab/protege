package org.protege.ai.api;

import org.protege.editor.owl.OWLEditorKit;

/** A plugin's public, zero-argument factory creates a fresh tool for each editor kit. */
public interface AgentToolFactory<I, O> {

    /** Describes the tool without an editor kit or tool instance. Must be quick and stable. */
    AgentToolDefinition<I, O> getDefinition();

    /** Creates a tool with the same definition and its dependencies; the registry calls initialise afterwards. */
    AgentTool<I, O> createTool(OWLEditorKit editorKit);
}
