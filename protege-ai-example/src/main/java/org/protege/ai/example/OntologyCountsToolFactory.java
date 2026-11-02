package org.protege.ai.example;

import org.protege.ai.api.AgentTool;
import org.protege.ai.api.AgentToolDescription;
import org.protege.ai.api.AgentToolFactory;
import org.protege.editor.owl.OWLEditorKit;

public final class OntologyCountsToolFactory implements AgentToolFactory {

    @Override
    public AgentToolDescription getDescription() {
        return new AgentToolDescription("example_ontology_counts",
                "Count asserted classes and axioms in the active ontology, excluding imports.",
                """
                {"type":"object","properties":{},"additionalProperties":false}
                """.strip(),
                "JSON object with integer classCount and axiomCount fields, excluding imported ontologies.");
    }

    @Override
    public AgentTool createTool(OWLEditorKit editorKit) {
        return new OntologyCountsTool(editorKit.getOWLModelManager()::getActiveOntology);
    }
}
