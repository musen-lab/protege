package org.protege.ai.example;

import org.protege.ai.api.AgentTool;
import org.protege.ai.api.AgentToolDefinition;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.parameters.Imports;

import java.util.Objects;
import java.util.function.Supplier;

/** A small read-only example, counting asserted content in the active ontology only. */
public final class OntologyCountsTool implements AgentTool<OntologyCountsArguments, OntologyCountsResult> {

    static final AgentToolDefinition<OntologyCountsArguments, OntologyCountsResult> DEFINITION =
            new AgentToolDefinition<>("example_ontology_counts",
                    "Count asserted classes and axioms in the active ontology, excluding imports.",
                    OntologyCountsArguments.class, OntologyCountsResult.class);

    private final Supplier<OWLOntology> activeOntology;

    public OntologyCountsTool(Supplier<OWLOntology> activeOntology) {
        this.activeOntology = Objects.requireNonNull(activeOntology);
    }

    @Override
    public AgentToolDefinition<OntologyCountsArguments, OntologyCountsResult> getDefinition() {
        return DEFINITION;
    }

    @Override
    public OntologyCountsResult execute(OntologyCountsArguments arguments) {
        // Resolve once per call, so changing the active ontology does not leave a stale reference.
        OWLOntology ontology = Objects.requireNonNull(activeOntology.get(), "No active ontology");
        return new OntologyCountsResult(ontology.getClassesInSignature(Imports.EXCLUDED).size(),
                ontology.getAxiomCount(Imports.EXCLUDED));
    }
}
