package org.protege.editor.owl.model.io.jsonld;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.jsonldjava.utils.JsonUtils;
import org.junit.Before;
import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.io.StreamDocumentTarget;
import org.semanticweb.owlapi.model.*;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

public class BlankNodeNestingTest {

    private static final String NS = "http://example.org/zoo#";

    private final ObjectMapper mapper = new ObjectMapper();

    private OWLOntology ontology;

    private JsonLdPreferences prefs;

    @Before
    public void setUp() throws Exception {
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = manager.getOWLDataFactory();
        ontology = manager.createOntology(IRI.create("http://example.org/zoo"));
        OWLClass cat = df.getOWLClass(IRI.create(NS + "Cat"));
        OWLObjectProperty eats = df.getOWLObjectProperty(IRI.create(NS + "eats"));
        manager.addAxiom(ontology, df.getOWLSubClassOfAxiom(cat,
                df.getOWLObjectSomeValuesFrom(eats, df.getOWLObjectUnionOf(
                        df.getOWLClass(IRI.create(NS + "Mouse")),
                        df.getOWLObjectSomeValuesFrom(eats, df.getOWLClass(IRI.create(NS + "Cheese")))))));
        prefs = new JsonLdPreferences(InMemoryPreferences::new);
    }

    private JsonNode save() throws Exception {
        ConfigurableJsonLDDocumentFormat format = new ConfigurableJsonLDDocumentFormat();
        format.setPrefix("ex:", NS);
        format.setPrefix("owl:", "http://www.w3.org/2002/07/owl#");
        format.setPrefix("rdfs:", "http://www.w3.org/2000/01/rdf-schema#");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new ConfigurableJsonLdStorer(prefs).storeOntology(ontology, new StreamDocumentTarget(out), format);
        return mapper.readTree(out.toString(StandardCharsets.UTF_8.name()));
    }

    private static Set<String> topLevelBlankNodes(JsonNode root) {
        Set<String> ids = new HashSet<>();
        for (JsonNode node : root.isArray() ? root : root.get("@graph")) {
            String id = node.path("@id").asText();
            if (id.startsWith("_:")) {
                ids.add(id);
            }
        }
        return ids;
    }

    private static JsonNode cat(JsonNode root) {
        for (JsonNode node : root.get("@graph")) {
            if ("ex:Cat".equals(node.path("@id").asText())) {
                return node;
            }
        }
        fail("No ex:Cat");
        return null;
    }

    @Test
    public void restrictionsAreNestedInsideTheClass() throws Exception {
        JsonNode root = save();
        assertTrue("No separate blank nodes should be left", topLevelBlankNodes(root).isEmpty());

        JsonNode restriction = cat(root).get("rdfs:subClassOf");
        assertEquals("owl:Restriction", restriction.get("@type").asText());
        assertFalse("A nested blank node has no id", restriction.has("@id"));
        // The union and the restriction inside its list are nested too
        JsonNode union = restriction.get("owl:someValuesFrom");
        JsonNode list = union.get("owl:unionOf").get("@list");
        assertEquals("owl:Restriction", list.get(1).get("@type").asText());
    }

    @Test
    public void referenceKeepsSeparateNodes() throws Exception {
        prefs.setBlankNodeEmbedding(BlankNodeEmbedding.REFERENCE);
        JsonNode root = save();
        assertEquals(3, topLevelBlankNodes(root).size());
        // rdfs:subClassOf has "@type": "@id", so the reference is a plain string
        assertTrue(cat(root).get("rdfs:subClassOf").asText().startsWith("_:"));
    }

    @Test
    public void flattenedIgnoresNesting() throws Exception {
        prefs.setDocumentForm(DocumentForm.FLATTENED);
        assertEquals(3, topLevelBlankNodes(save()).size());
    }

    @Test
    public void expandedFormCanBeNestedToo() throws Exception {
        prefs.setDocumentForm(DocumentForm.EXPANDED);
        assertTrue(topLevelBlankNodes(save()).isEmpty());
    }

    @Test
    public void aBlankNodeUsedTwiceStaysSeparate() throws Exception {
        Object doc = JsonUtils.fromString("[" +
                "{\"@id\":\"http://x/a\",\"http://x/p\":[{\"@id\":\"_:shared\"}]}," +
                "{\"@id\":\"http://x/b\",\"http://x/p\":[{\"@id\":\"_:shared\"}]}," +
                "{\"@id\":\"_:shared\",\"http://x/q\":[{\"@value\":\"v\"}]}]");
        JsonNode result = mapper.valueToTree(JsonLdPostProcessor.nestBlankNodes(doc));
        assertEquals(3, result.size());
        assertEquals("_:shared", result.get(0).get("http://x/p").get(0).get("@id").asText());
        assertEquals("_:shared", result.get(2).get("@id").asText());
    }

    @Test
    public void blankNodesInALoopAreNotLost() throws Exception {
        Object doc = JsonUtils.fromString("[" +
                "{\"@id\":\"_:a\",\"http://x/p\":[{\"@id\":\"_:b\"}]}," +
                "{\"@id\":\"_:b\",\"http://x/p\":[{\"@id\":\"_:a\"}]}]");
        JsonNode result = mapper.valueToTree(JsonLdPostProcessor.nestBlankNodes(doc));
        String text = result.toString();
        assertTrue(text.contains("\"@id\":\"_:a\""));
        assertTrue(text.contains("\"@id\":\"_:b\""));
        assertEquals("Both links of the loop are kept", 2, text.split("http://x/p").length - 1);
    }

    @Test
    public void anUnreferencedBlankNodeStays() throws Exception {
        Object doc = JsonUtils.fromString("[{\"@id\":\"_:alone\",\"http://x/q\":[{\"@value\":\"v\"}]}]");
        JsonNode result = mapper.valueToTree(JsonLdPostProcessor.nestBlankNodes(doc));
        assertEquals("_:alone", result.get(0).get("@id").asText());
    }
}
