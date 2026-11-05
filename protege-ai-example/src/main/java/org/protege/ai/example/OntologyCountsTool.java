package org.protege.ai.example;

import org.protege.ai.api.AgentTool;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.parameters.Imports;

import java.util.Objects;
import java.util.function.Supplier;

/** A small read-only example, counting asserted content in the active ontology only. */
public final class OntologyCountsTool implements AgentTool {

    private final Supplier<OWLOntology> activeOntology;

    public OntologyCountsTool(Supplier<OWLOntology> activeOntology) {
        this.activeOntology = Objects.requireNonNull(activeOntology);
    }

    @Override
    public String execute(String input) {
        // Resolve once per call, so changing the active ontology does not leave a stale reference.
        OWLOntology ontology = Objects.requireNonNull(activeOntology.get(), "No active ontology");
        return "{\"classCount\":" + ontology.getClassesInSignature(Imports.EXCLUDED).size()
                + ",\"axiomCount\":" + ontology.getAxiomCount(Imports.EXCLUDED) + "}";
    }
}
