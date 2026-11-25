package org.protege.ai.example;

import org.junit.Test;
import org.protege.ai.api.AgentTool;
import org.protege.ai.api.AgentToolDefinition;
import org.protege.ai.api.AgentToolResult;
import org.protege.ai.api.AgentToolSource;
import org.protege.ai.core.AgentToolJsonService;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLOntology;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class OntologyCountsToolTest {

    @Test
    public void factoryAndToolsShareMetadataWithoutAccessingAnOntology() {
        var factory = new OntologyCountsToolFactory();
        var first = new OntologyCountsTool(() -> { throw new AssertionError("Metadata needs no ontology"); });
        var second = new OntologyCountsTool(() -> { throw new AssertionError("Metadata needs no ontology"); });
        var definition = factory.getDefinition();
        assertSame(definition, first.getDefinition());
        assertSame(definition, second.getDefinition());
        assertEquals(definition.name(), first.getName());
        assertEquals(definition.description(), first.getDescription());
        Class<OntologyCountsArguments> inputType = first.getInputType();
        assertSame(OntologyCountsArguments.class, inputType);
    }

    @Test
    public void invokesRegisteredToolAgainstPizzaAndFollowsActiveOntologyChanges() throws Exception {
        var manager = OWLManager.createOWLOntologyManager();
        OWLOntology pizza;
        try (var stream = getClass().getResourceAsStream("/ontologies/pizza.owl")) {
            assertNotNull(stream);
            pizza = manager.loadOntologyFromOntologyDocument(stream);
        }
        var active = new AtomicReference<>(pizza);
        AgentToolSource<OntologyCountsArguments, OntologyCountsResult> source = new AgentToolSource<>() {
            public String getId() { return "example.OntologyCounts"; }
            public AgentToolDefinition<OntologyCountsArguments, OntologyCountsResult> getDefinition() {
                return new OntologyCountsToolFactory().getDefinition();
            }
            public AgentTool<OntologyCountsArguments, OntologyCountsResult> newInstance() { return new OntologyCountsTool(active::get); }
        };
        var jsonTools = new AgentToolJsonService(List.of(source));
        var result = jsonTools.execute("example_ontology_counts", "{}");
        assertTrue(result.isSuccess());
        assertTrue(result instanceof AgentToolResult.Success<?>);
        var success = (AgentToolResult.Success<?>) result;
        assertEquals(new OntologyCountsResult(pizza.getClassesInSignature().size(), pizza.getAxiomCount()),
                success.value());
        assertEquals(result.content(), success.json());
        assertTrue(pizza.getClassesInSignature().size() > 0);
        assertEquals("{\"classCount\":" + pizza.getClassesInSignature().size()
                + ",\"axiomCount\":" + pizza.getAxiomCount() + "}", result.content());
        active.set(manager.createOntology());
        assertEquals("{\"classCount\":0,\"axiomCount\":0}",
                jsonTools.execute("example_ontology_counts", "{}").content());
        jsonTools.dispose();
    }
}
