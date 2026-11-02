package org.protege.ai.example;

import org.protege.ai.api.AgentTool;
import org.protege.ai.api.AgentToolFactory;
import org.protege.editor.owl.OWLEditorKit;

public final class OntologyCountsToolFactory implements AgentToolFactory {

    @Override
    public AgentTool createTool(OWLEditorKit editorKit) {
        return new OntologyCountsTool(editorKit.getOWLModelManager()::getActiveOntology);
    }
}
