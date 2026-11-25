package org.protege.ai.example;

import org.junit.Test;
import org.protege.ai.api.AgentTool;
import org.protege.ai.api.AgentToolDefinition;
import org.protege.ai.api.AgentToolResult;
import org.protege.ai.api.AgentToolSource;
import org.protege.ai.core.AgentToolJsonService;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;

import java.util.List;

import static org.junit.Assert.*;
import static org.protege.ai.api.ToolStatus.INVALID_INPUT;

public class ClassReferenceToolTest {

    @Test
    public void convertsExistingAndUnknownClassReferencesWithoutChangingPizza() throws Exception {
        var manager = OWLManager.createOWLOntologyManager();
        try (var stream = getClass().getResourceAsStream("/ontologies/pizza.owl")) {
            assertNotNull(stream);
            var ontology = manager.loadOntologyFromOntologyDocument(stream);
            var axioms = ontology.getAxioms();
            var factory = new ClassReferenceToolFactory();
            var tool = factory.createTool(null); // This tool needs no window or ontology.
            assertSame(factory.getDefinition(), tool.getDefinition());
            var source = new AgentToolSource<OWLClass, OWLClass>() {
                public String getId() { return "example.ClassReference"; }
                public AgentToolDefinition<OWLClass, OWLClass> getDefinition() { return factory.getDefinition(); }
                public AgentTool<OWLClass, OWLClass> newInstance() { return tool; }
            };
            var jsonTools = new AgentToolJsonService(List.of(source), manager.getOWLDataFactory());
            try {
                var known = ontology.getClassesInSignature().iterator().next().getIRI();
                var unknown = IRI.create("https://example.org/NotInPizza");
                assertFalse(ontology.containsClassInSignature(unknown));
                for (var iri : List.of(known, unknown)) {
                    var result = jsonTools.execute(tool.getName(), "\"" + iri + "\"");
                    assertTrue(result.content(), result instanceof AgentToolResult.Success<?>);
                    var success = (AgentToolResult.Success<?>) result;
                    assertEquals(manager.getOWLDataFactory().getOWLClass(iri), success.value());
                    assertEquals("\"" + iri + "\"", success.json());
                }
                assertEquals(INVALID_INPUT, jsonTools.execute(tool.getName(), "\"Pizza\"").status());
                assertEquals(axioms, ontology.getAxioms());
                assertFalse(ontology.containsClassInSignature(unknown));
            } finally {
                jsonTools.dispose();
            }
        }
    }
}
