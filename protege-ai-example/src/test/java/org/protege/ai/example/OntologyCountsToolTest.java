package org.protege.ai.example;

import org.junit.Test;
import org.protege.ai.api.AgentTool;
import org.protege.ai.api.AgentToolDescription;
import org.protege.ai.api.AgentToolSource;
import org.protege.ai.core.AgentToolRegistry;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLOntology;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class OntologyCountsToolTest {

    @Test
    public void invokesRegisteredToolAgainstPizzaAndFollowsActiveOntologyChanges() throws Exception {
        var manager = OWLManager.createOWLOntologyManager();
        OWLOntology pizza;
        try (var stream = getClass().getResourceAsStream("/ontologies/pizza.owl")) {
            assertNotNull(stream);
            pizza = manager.loadOntologyFromOntologyDocument(stream);
        }
        var active = new AtomicReference<>(pizza);
        AgentToolSource source = new AgentToolSource() {
            public String getId() { return "example.OntologyCounts"; }
            public AgentToolDescription getDescription() {
                return new AgentToolDescription("example_ontology_counts", "Counts", "{\"type\":\"object\"}", "application/json");
            }
            public AgentTool newInstance() { return new OntologyCountsTool(active::get); }
        };
        var registry = new AgentToolRegistry(List.of(source));
        var result = registry.execute("example_ontology_counts", "{}");
        assertTrue(result.isSuccess());
        assertTrue(pizza.getClassesInSignature().size() > 0);
        assertEquals("{\"classCount\":" + pizza.getClassesInSignature().size()
                + ",\"axiomCount\":" + pizza.getAxiomCount() + "}", result.content());
        active.set(manager.createOntology());
        assertEquals("{\"classCount\":0,\"axiomCount\":0}",
                registry.execute("example_ontology_counts", "{}").content());
        registry.dispose();
    }
}
