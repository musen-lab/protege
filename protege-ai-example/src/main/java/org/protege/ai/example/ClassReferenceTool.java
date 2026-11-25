package org.protege.ai.example;

import org.protege.ai.api.AgentTool;
import org.protege.ai.api.AgentToolDefinition;
import org.semanticweb.owlapi.model.OWLClass;

/** Demonstrates class conversion without looking up or changing an ontology. */
public final class ClassReferenceTool implements AgentTool<OWLClass, OWLClass> {

    static final AgentToolDefinition<OWLClass, OWLClass> DEFINITION = new AgentToolDefinition<>(
            "example_class_reference", "Return a class reference unchanged. Does not check ontology membership.",
            OWLClass.class, OWLClass.class);

    @Override
    public AgentToolDefinition<OWLClass, OWLClass> getDefinition() {
        return DEFINITION;
    }

    @Override
    public OWLClass execute(OWLClass owlClass) {
        return owlClass;
    }
}
