package org.protege.ai.example;

import org.protege.ai.api.AgentTool;
import org.protege.ai.api.AgentToolDefinition;
import org.protege.ai.api.AgentToolFactory;
import org.protege.editor.owl.OWLEditorKit;

public final class OntologyCountsToolFactory implements AgentToolFactory<OntologyCountsArguments, OntologyCountsResult> {

    @Override
    public AgentToolDefinition<OntologyCountsArguments, OntologyCountsResult> getDefinition() {
        return OntologyCountsTool.DEFINITION;
    }

    @Override
    public AgentTool<OntologyCountsArguments, OntologyCountsResult> createTool(OWLEditorKit editorKit) {
        return new OntologyCountsTool(editorKit.getOWLModelManager()::getActiveOntology);
    }
}
