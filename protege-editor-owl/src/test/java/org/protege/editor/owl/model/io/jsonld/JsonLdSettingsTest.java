package org.protege.editor.owl.model.io.jsonld;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.RDFJsonLDDocumentFormat;
import org.semanticweb.owlapi.io.StreamDocumentTarget;
import org.semanticweb.owlapi.io.StringDocumentSource;
import org.semanticweb.owlapi.model.*;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Checks that each JSON-LD setting changes the saved output as described in the plan.
 */
public class JsonLdSettingsTest {

    private static final String NS = "http://example.org/zoo#";

    private static final String A_NS = "http://a.org/ns#";

    private static final String B_NS = "http://b.org/ns#";

    private final ObjectMapper mapper = new ObjectMapper();

    private OWLOntologyManager manager;

    private OWLDataFactory df;

    private OWLOntology ontology;

    private JsonLdPreferences prefs;

    @Before
    public void setUp() throws Exception {
        manager = OWLManager.createOWLOntologyManager();
        df = manager.getOWLDataFactory();
        ontology = manager.createOntology(IRI.create("http://example.org/zoo"));

        OWLClass cat = df.getOWLClass(IRI.create(NS + "Cat"));
        OWLObjectProperty eats = df.getOWLObjectProperty(IRI.create(NS + "eats"));
        OWLDataProperty age = df.getOWLDataProperty(IRI.create(NS + "age"));
        OWLNamedIndividual tom = df.getOWLNamedIndividual(IRI.create(NS + "tom"));

        manager.addAxiom(ontology, df.getOWLDeclarationAxiom(cat));
        manager.addAxiom(ontology, df.getOWLDeclarationAxiom(eats));
        manager.addAxiom(ontology, df.getOWLDeclarationAxiom(age));
        manager.addAxiom(ontology, df.getOWLClassAssertionAxiom(cat, tom));
        manager.addAxiom(ontology, df.getOWLObjectPropertyAssertionAxiom(eats, tom, tom));
        manager.addAxiom(ontology, df.getOWLDataPropertyAssertionAxiom(age, tom, df.getOWLLiteral(30)));
        annotate(cat.getIRI(), df.getRDFSLabel(), df.getOWLLiteral("Cat", "en"));
        annotate(cat.getIRI(), df.getRDFSLabel(), df.getOWLLiteral("Chat", "fr"));
        annotate(age.getIRI(), df.getRDFSLabel(), df.getOWLLiteral("has age", "en"));

        prefs = new JsonLdPreferences(InMemoryPreferences::new);
    }

    private void annotate(IRI subject, OWLAnnotationProperty property, OWLAnnotationValue value) {
        manager.addAxiom(ontology, df.getOWLAnnotationAssertionAxiom(subject, df.getOWLAnnotation(property, value)));
    }

    /** Adds two annotation properties that both have the local name "name". */
    private void addNameClash() {
        OWLAnnotationProperty aName = df.getOWLAnnotationProperty(IRI.create(A_NS + "name"));
        OWLAnnotationProperty bName = df.getOWLAnnotationProperty(IRI.create(B_NS + "name"));
        IRI tom = IRI.create(NS + "tom");
        annotate(tom, aName, df.getOWLLiteral("Tom"));
        annotate(tom, bName, df.getOWLLiteral("Thomas"));
    }

    private ConfigurableJsonLDDocumentFormat format() {
        ConfigurableJsonLDDocumentFormat format = new ConfigurableJsonLDDocumentFormat();
        format.setPrefix("ex:", NS);
        format.setPrefix("owl:", "http://www.w3.org/2002/07/owl#");
        format.setPrefix("rdf:", "http://www.w3.org/1999/02/22-rdf-syntax-ns#");
        format.setPrefix("rdfs:", "http://www.w3.org/2000/01/rdf-schema#");
        format.setPrefix("xml:", "http://www.w3.org/XML/1998/namespace");
        format.setPrefix("xsd:", "http://www.w3.org/2001/XMLSchema#");
        format.setPrefix("a:", A_NS);
        format.setPrefix("b:", B_NS);
        return format;
    }

    private String saveText() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new ConfigurableJsonLdStorer(prefs).storeOntology(ontology, new StreamDocumentTarget(out), format());
        return out.toString(StandardCharsets.UTF_8.name());
    }

    private JsonNode save() throws Exception {
        return mapper.readTree(saveText());
    }

    private void assertNothingLost(String jsonLd) throws Exception {
        OWLOntology reloaded = OWLManager.createOWLOntologyManager().loadOntologyFromOntologyDocument(
                new StringDocumentSource(jsonLd, IRI.create("http://example.org/doc"), new RDFJsonLDDocumentFormat(), null));
        assertEquals(content(ontology), content(reloaded));
    }

    private static Set<OWLAxiom> content(OWLOntology ont) {
        Set<OWLAxiom> axioms = new HashSet<>(ont.getLogicalAxioms());
        axioms.addAll(ont.getAxioms(AxiomType.ANNOTATION_ASSERTION));
        return axioms;
    }

    private static JsonNode node(JsonNode root, String id) {
        for (JsonNode n : root.get("@graph")) {
            if (id.equals(n.path("@id").asText())) {
                return n;
            }
        }
        fail("No node " + id);
        return null;
    }

    private static List<String> keys(JsonNode node) {
        List<String> keys = new ArrayList<>();
        node.fieldNames().forEachRemaining(keys::add);
        return keys;
    }

    // --- Context & Prefixes ---

    @Test
    public void ontologyPrefixesCanBeLeftOut() throws Exception {
        assertTrue(save().get("@context").has("ex"));

        prefs.setIncludeOntologyPrefixes(false);
        JsonNode root = save();
        assertFalse(root.get("@context").has("ex"));
        assertNotNull("Without prefixes, IRIs are written in full", node(root, NS + "tom"));
    }

    @Test
    public void unusedPrefixesAreLeftOutWhenPruning() throws Exception {
        JsonNode context = save().get("@context");
        assertTrue(context.has("ex"));
        assertTrue("owl is used by owl:Class", context.has("owl"));
        assertFalse("xml is not used", context.has("xml"));
        assertFalse("xsd is not used with native numbers", context.has("xsd"));

        prefs.setPruneUnusedPrefixes(false);
        context = save().get("@context");
        assertTrue(context.has("xml"));
        assertTrue(context.has("xsd"));
    }

    @Test
    public void aPrefixNamedLikeItsOwnSchemeIsLeftOut() throws Exception {
        // "urn" -> "urn:example:" would refer to itself in JSON-LD; the save must still work
        OWLClass thing = df.getOWLClass(IRI.create("urn:example:Thing"));
        manager.addAxiom(ontology, df.getOWLDeclarationAxiom(thing));
        ConfigurableJsonLDDocumentFormat format = format();
        format.setPrefix("urn:", "urn:example:");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new ConfigurableJsonLdStorer(prefs).storeOntology(ontology, new StreamDocumentTarget(out), format);
        String text = out.toString(StandardCharsets.UTF_8.name());
        assertFalse(mapper.readTree(text).get("@context").has("urn"));
        assertTrue(text.contains("urn:example:Thing"));
        assertNothingLost(text);
    }

    @Test
    public void noShortNamesWhenTermsAreOff() throws Exception {
        prefs.setGenerateTermsFor(GenerateTermsFor.NONE);
        JsonNode root = save();
        assertFalse(root.get("@context").has("eats"));
        assertFalse(root.get("@context").has("age"));
        // eats still needs "@type": "@id", so it is defined under its prefixed name
        assertEquals("@id", root.get("@context").get("ex:eats").get("@type").asText());
        assertTrue(node(root, "ex:tom").has("ex:age"));
    }

    @Test
    public void annotationPropertiesOnly() throws Exception {
        prefs.setGenerateTermsFor(GenerateTermsFor.ANNOTATION_PROPERTIES);
        JsonNode context = save().get("@context");
        assertTrue(context.has("label"));
        assertFalse(context.has("age"));
        assertFalse(context.has("eats"));
    }

    @Test
    public void allEntitiesGivesClassesShortNamesToo() throws Exception {
        assertFalse(save().get("@context").has("Cat"));

        prefs.setGenerateTermsFor(GenerateTermsFor.ALL_ENTITIES);
        String text = saveText();
        JsonNode root = mapper.readTree(text);
        assertEquals(NS + "Cat", root.get("@context").get("Cat").asText());
        assertTrue("tom's type uses the short name", node(root, "ex:tom").get("@type").toString().contains("\"Cat\""));
        assertNothingLost(text);
    }

    @Test
    public void namesFromLabels() throws Exception {
        prefs.setTermNameSource(TermNameSource.RDFS_LABEL);
        String text = saveText();
        JsonNode root = mapper.readTree(text);
        assertEquals("Label 'has age' becomes has_age", NS + "age", root.get("@context").get("has_age").asText());
        assertTrue(node(root, "ex:tom").has("has_age"));
        assertNothingLost(text);
    }

    @Test
    public void clashPrefixedIriGivesTheNameToNobody() throws Exception {
        addNameClash();
        prefs.setTermCollisionPolicy(TermCollisionPolicy.PREFIXED_IRI);
        JsonNode root = save();
        assertFalse(root.get("@context").has("name"));
        assertTrue(node(root, "ex:tom").has("a:name"));
        assertTrue(node(root, "ex:tom").has("b:name"));
    }

    @Test
    public void clashFirstWinsGivesTheNameToTheFirstIri() throws Exception {
        addNameClash();
        prefs.setTermCollisionPolicy(TermCollisionPolicy.FIRST_WINS);
        JsonNode root = save();
        assertEquals(A_NS + "name", root.get("@context").get("name").asText());
        assertTrue(node(root, "ex:tom").has("name"));
        assertTrue(node(root, "ex:tom").has("b:name"));
    }

    @Test
    public void clashDisambiguateNumbersTheOthers() throws Exception {
        addNameClash();
        prefs.setTermCollisionPolicy(TermCollisionPolicy.DISAMBIGUATE);
        String text = saveText();
        JsonNode context = mapper.readTree(text).get("@context");
        assertEquals(A_NS + "name", context.get("name").asText());
        assertEquals(B_NS + "name", context.get("name_1").asText());
        assertNothingLost(text);
    }

    @Test
    public void clashErrorStopsTheSave() throws Exception {
        addNameClash();
        prefs.setTermCollisionPolicy(TermCollisionPolicy.ERROR);
        try {
            saveText();
            fail("Expected the save to stop");
        } catch (OWLOntologyStorageException e) {
            assertTrue(e.getMessage().contains("'name'"));
            assertTrue(e.getMessage().contains(A_NS + "name"));
            assertTrue(e.getMessage().contains(B_NS + "name"));
        }
    }

    @Test
    public void clashErrorDoesNotStopAnExpandedSave() throws Exception {
        addNameClash();
        prefs.setTermCollisionPolicy(TermCollisionPolicy.ERROR);
        prefs.setDocumentForm(DocumentForm.EXPANDED);
        String text = saveText();
        assertTrue(mapper.readTree(text).isArray());
        assertNothingLost(text);
    }

    @Test
    public void noClashNoError() throws Exception {
        prefs.setTermCollisionPolicy(TermCollisionPolicy.ERROR);
        assertTrue(save().has("@context"));
    }

    // --- Output Shape & Formatting ---

    @Test
    public void compactedFilesAlwaysUseGraph() throws Exception {
        // OWL API always writes an ontology header node, so an empty ontology gives a single node
        ontology = manager.createOntology(IRI.create("http://example.org/empty"));
        assertTrue(save().get("@graph").isArray());
    }

    @Test
    public void singleValuesStayInArraysWhenNotCompacting() throws Exception {
        assertTrue(node(save(), "ex:tom").get("age").isNumber());
        prefs.setCompactArrays(false);
        assertTrue(node(save(), "ex:tom").get("age").isArray());
    }

    @Test
    public void keyOrdering() throws Exception {
        List<String> keywordsFirst = keys(node(save(), "ex:tom"));
        assertEquals("@id", keywordsFirst.get(0));
        assertEquals("@type", keywordsFirst.get(1));

        prefs.setKeyOrdering(KeyOrdering.LEXICOGRAPHIC);
        List<String> lexicographic = keys(node(save(), "ex:tom"));
        List<String> sorted = new ArrayList<>(lexicographic);
        sorted.sort(String::compareTo);
        assertEquals(sorted, lexicographic);
    }

    @Test
    public void prettyPrintAndIndent() throws Exception {
        assertTrue(saveText().contains("\n  \"@context\""));

        prefs.setIndentSpaces(4);
        assertTrue(saveText().contains("\n    \"@context\""));

        prefs.setPrettyPrint(false);
        assertFalse(saveText().trim().contains("\n"));
    }

    // --- Literals & Types ---

    @Test
    public void objectPropertyValuesArePlainIrisOnlyWithCoercion() throws Exception {
        assertEquals("ex:tom", node(save(), "ex:tom").get("eats").asText());

        prefs.setTypeCoercionMode(TypeCoercionMode.NONE);
        JsonNode root = save();
        assertFalse(root.get("@context").get("eats").isObject());
        assertEquals("ex:tom", node(root, "ex:tom").get("eats").get("@id").asText());
    }

    @Test
    public void owlAndRdfsLinksArePlainIrisWithCoercion() throws Exception {
        OWLClass cat = df.getOWLClass(IRI.create(NS + "Cat"));
        OWLClass animal = df.getOWLClass(IRI.create(NS + "Animal"));
        OWLObjectProperty eats = df.getOWLObjectProperty(IRI.create(NS + "eats"));
        manager.addAxiom(ontology, df.getOWLSubClassOfAxiom(cat, animal));
        manager.addAxiom(ontology, df.getOWLSubClassOfAxiom(cat, df.getOWLObjectAllValuesFrom(eats, animal)));

        String text = saveText();
        JsonNode root = mapper.readTree(text);
        assertEquals("@id", root.get("@context").get("rdfs:subClassOf").get("@type").asText());
        String catNode = node(root, "ex:Cat").toString();
        assertTrue(catNode, catNode.contains("\"ex:Animal\""));
        assertTrue(catNode, catNode.contains("\"owl:onProperty\":\"ex:eats\""));
        assertFalse("Vocabulary that isn't used gets no entry", root.get("@context").has("owl:equivalentClass"));
        assertNothingLost(text);

        prefs.setTypeCoercionMode(TypeCoercionMode.NONE);
        root = save();
        assertFalse(root.get("@context").has("rdfs:subClassOf"));
        assertTrue(node(root, "ex:Cat").toString().contains("\"owl:onProperty\":{\"@id\":\"ex:eats\"}"));
    }

    @Test
    public void typesAreAllInTheTypeKeyword() throws Exception {
        // Cat is also described in the file; its type must not end up under rdf:type
        JsonNode tom = node(save(), "ex:tom");
        assertFalse(tom.has("rdf:type"));
        String types = tom.get("@type").toString();
        assertTrue(types.contains("ex:Cat"));
        assertTrue(types.contains("owl:NamedIndividual"));
    }

    @Test
    public void rdfTypeAsANormalProperty() throws Exception {
        prefs.setUseRdfType(true);
        String text = saveText();
        JsonNode tom = node(mapper.readTree(text), "ex:tom");
        assertFalse(tom.has("@type"));
        assertTrue(tom.has("rdf:type"));
        assertNothingLost(text);
    }

    @Test
    public void untaggedTextNextToLanguageMapsIsKept() throws Exception {
        // rdfs:label now has language-tagged values and an untagged one
        annotate(IRI.create(NS + "tom"), df.getRDFSLabel(), df.getOWLLiteral("Tom, no language tag"));
        for (GenerateTermsFor terms : GenerateTermsFor.values()) {
            prefs.setGenerateTermsFor(terms);
            String text = saveText();
            JsonNode context = mapper.readTree(text).get("@context");
            if (terms == GenerateTermsFor.NONE) {
                // No short name: a language map would be rdfs:label's only key, so none is used
                assertFalse(context.has("rdfs:label"));
            } else {
                assertEquals("@language", context.get("label").get("@container").asText());
                assertEquals("Tom, no language tag", node(mapper.readTree(text), "ex:tom").get("rdfs:label").asText());
            }
            assertNothingLost(text);
        }
    }

    @Test
    public void languageMapsWithoutShortNamesWhenAllValuesAreTagged() throws Exception {
        prefs.setGenerateTermsFor(GenerateTermsFor.NONE);
        JsonNode root = save();
        // Only language-tagged labels in this ontology, so the prefixed name can carry the language map
        assertEquals("@language", root.get("@context").get("rdfs:label").get("@container").asText());
        assertEquals("Chat", node(root, "ex:Cat").get("rdfs:label").get("fr").asText());
    }

    @Test
    public void languageMapsCanBeTurnedOff() throws Exception {
        assertEquals("Chat", node(save(), "ex:Cat").get("label").get("fr").asText());

        prefs.setUseLanguageMaps(false);
        String text = saveText();
        JsonNode root = mapper.readTree(text);
        assertFalse(root.get("@context").get("label").isObject());
        JsonNode labels = node(root, "ex:Cat").get("label");
        assertTrue(labels.isArray());
        assertTrue(labels.toString().contains("\"@language\":\"fr\""));
        assertNothingLost(text);
    }
}
