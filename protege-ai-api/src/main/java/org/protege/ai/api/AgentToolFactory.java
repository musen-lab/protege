package org.protege.ai.api;

import org.protege.editor.owl.OWLEditorKit;

/** A plugin's public, zero-argument factory creates a fresh tool for each editor kit. */
@FunctionalInterface
public interface AgentToolFactory {

    /** Capture the dependencies the tool needs; the registry calls initialise afterwards. */
    AgentTool createTool(OWLEditorKit editorKit);
}
