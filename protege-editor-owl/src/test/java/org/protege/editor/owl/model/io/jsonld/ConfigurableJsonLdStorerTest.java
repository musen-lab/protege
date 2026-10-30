package org.protege.editor.owl.model.io.jsonld;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.RDFJsonLDDocumentFormat;
import org.semanticweb.owlapi.io.StreamDocumentTarget;
import org.semanticweb.owlapi.io.StringDocumentSource;
import org.semanticweb.owlapi.io.StringDocumentTarget;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.rio.RioJsonLDStorerFactory;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

public class ConfigurableJsonLdStorerTest {

    private static final String NS = "http://example.org/zoo#";

    private static final String OTHER_NS = "http://other.org/ns#";

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
        OWLClass animal = df.getOWLClass(IRI.create(NS + "Animal"));
        OWLClass dog = df.getOWLClass(IRI.create(NS + "Dog"));
        OWLObjectProperty eats = df.getOWLObjectProperty(IRI.create(NS + "eats"));
        OWLDataProperty age = df.getOWLDataProperty(IRI.create(NS + "age"));
        // Local name "ex" is the same as a prefix name below
        OWLObjectProperty ex = df.getOWLObjectProperty(IRI.create(OTHER_NS + "ex"));
        OWLNamedIndividual tom = df.getOWLNamedIndividual(IRI.create(NS + "tom"));

        manager.addAxiom(ontology, df.getOWLDeclarationAxiom(cat));
        manager.addAxiom(ontology, df.getOWLDeclarationAxiom(eats));
        manager.addAxiom(ontology, df.getOWLDeclarationAxiom(age));
        manager.addAxiom(ontology, df.getOWLDeclarationAxiom(ex));
        manager.addAxiom(ontology, df.getOWLSubClassOfAxiom(cat,
                df.getOWLObjectSomeValuesFrom(eats, df.getOWLObjectUnionOf(animal, dog))));
        manager.addAxiom(ontology, df.getOWLClassAssertionAxiom(cat, tom));
        manager.addAxiom(ontology, df.getOWLObjectPropertyAssertionAxiom(eats, tom, tom));
        manager.addAxiom(ontology, df.getOWLObjectPropertyAssertionAxiom(ex, tom, tom));
        manager.addAxiom(ontology, df.getOWLDataPropertyAssertionAxiom(age, tom, df.getOWLLiteral(30)));
        addAnnotation(cat, df.getRDFSLabel(), df.getOWLLiteral("Cat", "en"));
        addAnnotation(cat, df.getRDFSLabel(), df.getOWLLiteral("Chat", "fr"));
        addAnnotation(cat, df.getRDFSComment(), df.getOWLLiteral("A cat", "en"));
        addAnnotation(cat, df.getRDFSComment(), df.getOWLLiteral("Feline", "en"));

        prefs = new JsonLdPreferences(InMemoryPreferences::new);
    }

    private void addAnnotation(OWLEntity subject, OWLAnnotationProperty property, OWLLiteral value) {
        manager.addAxiom(ontology, df.getOWLAnnotationAssertionAxiom(subject.getIRI(), df.getOWLAnnotation(property, value)));
    }

    private ConfigurableJsonLDDocumentFormat formatWithPrefixes() {
        ConfigurableJsonLDDocumentFormat format = new ConfigurableJsonLDDocumentFormat();
        format.setPrefix("ex:", NS);
        format.setPrefix("rdfs:", "http://www.w3.org/2000/01/rdf-schema#");
        format.setPrefix("owl:", "http://www.w3.org/2002/07/owl#");
        return format;
    }

    private String save(OWLOntology ont, OWLDocumentFormat format) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new ConfigurableJsonLdStorer(prefs).storeOntology(ont, new StreamDocumentTarget(out), format);
        return out.toString(StandardCharsets.UTF_8.name());
    }

    private static OWLOntology reload(String jsonLd) throws Exception {
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        // Don't try to download the imported ontology from the imports test
        m.setOntologyLoaderConfiguration(m.getOntologyLoaderConfiguration()
                .addIgnoredImport(IRI.create("http://example.org/imported")));
        return m.loadOntologyFromOntologyDocument(
                new StringDocumentSource(jsonLd, IRI.create("http://example.org/doc"), new RDFJsonLDDocumentFormat(), null));
    }

    private static Set<OWLAxiom> content(OWLOntology ont) {
        Set<OWLAxiom> axioms = new HashSet<>(ont.getLogicalAxioms());
        axioms.addAll(ont.getAxioms(AxiomType.ANNOTATION_ASSERTION));
        return axioms;
    }

    @Test
    public void everyPresetSavesAndReopensWithNothingLost() throws Exception {
        for (JsonLdPreset preset : JsonLdPreset.values()) {
            if (preset == JsonLdPreset.CUSTOM) {
                continue;
            }
            prefs.applyPreset(preset);
            OWLOntology reloaded = reload(save(ontology, formatWithPrefixes()));
            assertEquals("Content differs after save and reopen with preset " + preset, content(ontology), content(reloaded));
        }
    }

    @Test
    public void webFriendlyUsesPrefixesAndShortNames() throws Exception {
        JsonNode root = mapper.readTree(save(ontology, formatWithPrefixes()));

        JsonNode context = root.get("@context");
        assertNotNull("Root should have @context", context);
        assertEquals(NS, context.get("ex").asText());
        assertEquals("@id", context.get("eats").get("@type").asText());
        assertTrue("Body should use the short name 'eats'", root.toString().contains("\"eats\""));
        assertFalse("Body should not use the long form of eats", root.toString().contains("\"ex:eats\":"));
    }

    @Test
    public void aShortNameNeverReplacesAPrefix() throws Exception {
        JsonNode context = mapper.readTree(save(ontology, formatWithPrefixes())).get("@context");
        assertEquals("The ex prefix must stay the prefix", NS, context.get("ex").asText());
    }

    @Test
    public void languageMapsKeepEveryValue() throws Exception {
        String out = save(ontology, formatWithPrefixes());
        JsonNode cat = findNode(mapper.readTree(out), "ex:Cat");
        assertNotNull(cat);
        JsonNode comments = cat.get("comment").get("en");
        assertTrue("Both English comments should be kept", comments.isArray() && comments.size() == 2);
        assertEquals("Chat", cat.get("label").get("fr").asText());
    }

    @Test
    public void noVocabUnlessTurnedOn() throws Exception {
        ConfigurableJsonLDDocumentFormat format = formatWithPrefixes();
        format.setDefaultPrefix(NS);
        assertFalse(mapper.readTree(save(ontology, format)).get("@context").has("@vocab"));

        prefs.setUseVocab(true);
        assertEquals("http://example.org/zoo#", mapper.readTree(save(ontology, format)).get("@context").get("@vocab").asText());
    }

    @Test
    public void contextComesFirst() throws Exception {
        prefs.setKeyOrdering(KeyOrdering.NONE);
        String out = save(ontology, formatWithPrefixes());
        assertEquals("@context", mapper.readTree(out).fieldNames().next());
    }

    @Test
    public void expandedIsAnArrayWithoutContext() throws Exception {
        prefs.applyPreset(JsonLdPreset.EXPANDED);
        JsonNode root = mapper.readTree(save(ontology, formatWithPrefixes()));
        assertTrue(root.isArray());
        assertFalse(root.toString().contains("@context"));
    }

    @Test
    public void flattenedHasContextAndGraph() throws Exception {
        prefs.setDocumentForm(DocumentForm.FLATTENED);
        JsonNode root = mapper.readTree(save(ontology, formatWithPrefixes()));
        assertTrue(root.has("@context"));
        assertTrue(root.get("@graph").isArray());
    }

    @Test
    public void nativeNumbersFollowTheSetting() throws Exception {
        assertTrue(findNode(mapper.readTree(save(ontology, formatWithPrefixes())), "ex:tom").get("age").isNumber());
        prefs.setUseNativeTypes(false);
        assertFalse(findNode(mapper.readTree(save(ontology, formatWithPrefixes())), "ex:tom").get("age").isNumber());
    }

    @Test
    public void importsAreWrittenOnlyWhenTurnedOn() throws Exception {
        OWLOntology imported = manager.createOntology(IRI.create("http://example.org/imported"));
        OWLClass bird = df.getOWLClass(IRI.create(NS + "Bird"));
        OWLClass animal = df.getOWLClass(IRI.create(NS + "Animal"));
        manager.addAxiom(imported, df.getOWLSubClassOfAxiom(bird, animal));
        manager.applyChange(new AddImport(ontology, df.getOWLImportsDeclaration(imported.getOntologyID().getOntologyIRI().get())));
        OWLAxiom importedAxiom = df.getOWLSubClassOfAxiom(bird, animal);

        assertFalse(reload(save(ontology, formatWithPrefixes())).containsAxiom(importedAxiom));

        prefs.setIncludeImportsClosure(true);
        assertTrue(reload(save(ontology, formatWithPrefixes())).containsAxiom(importedAxiom));
    }

    @Test
    public void doesNotCloseTheCallersStream() throws Exception {
        boolean[] closed = {false};
        ByteArrayOutputStream out = new ByteArrayOutputStream() {
            @Override
            public void close() {
                closed[0] = true;
            }
        };
        new ConfigurableJsonLdStorer(prefs).storeOntology(ontology, new StreamDocumentTarget(out), formatWithPrefixes());
        assertFalse(closed[0]);
        assertTrue(out.size() > 0);
    }

    @Test
    public void writerTargetWorks() throws Exception {
        StringDocumentTarget target = new StringDocumentTarget();
        new ConfigurableJsonLdStorer(prefs).storeOntology(ontology, target, formatWithPrefixes());
        assertTrue(mapper.readTree(target.toString()).has("@context"));
    }

    @Test
    public void storesPlainJsonLdFormatToo() throws Exception {
        assertTrue(new ConfigurableJsonLdStorer(prefs).canStoreOntology(new RDFJsonLDDocumentFormat()));
        assertEquals(content(ontology), content(reload(save(ontology, new RDFJsonLDDocumentFormat()))));
    }

    @Test
    public void preservesAddMissingTypesWithoutRecreatingSuppressedDeclarations() throws Exception {
        OWLOntology undeclared = manager.createOntology(IRI.create("http://example.org/undeclared"));
        OWLDataProperty property = df.getOWLDataProperty(IRI.create(NS + "undeclaredProperty"));
        OWLNamedIndividual individual = df.getOWLNamedIndividual(IRI.create(NS + "individual"));
        OWLAxiom assertion = df.getOWLDataPropertyAssertionAxiom(property, individual, df.getOWLLiteral("value"));
        OWLAxiom declaration = df.getOWLDeclarationAxiom(property);
        manager.addAxiom(undeclared, assertion);

        for (OWLDocumentFormat format : new OWLDocumentFormat[]{
                new ConfigurableJsonLDDocumentFormat(), new RDFJsonLDDocumentFormat()}) {
            for (boolean addMissingTypes : new boolean[]{false, true}) {
                format.setAddMissingTypes(addMissingTypes);
                RDFJsonLDDocumentFormat standardFormat = new RDFJsonLDDocumentFormat();
                standardFormat.setAddMissingTypes(addMissingTypes);
                StringDocumentTarget standardOutput = new StringDocumentTarget();
                new RioJsonLDStorerFactory().createStorer()
                        .storeOntology(undeclared, standardOutput, standardFormat);
                OWLOntology reloaded = reload(save(undeclared, format));
                // Without type triples, OWL API can interpret the property as an annotation property.
                // Match its standard writer's round-trip behavior for the same flag.
                assertEquals(reload(standardOutput.toString()).getAxioms(), reloaded.getAxioms());
                assertEquals("The property declaration must follow addMissingTypes for " + format.getClass(),
                        addMissingTypes, reloaded.containsAxiom(declaration));
            }
        }
    }

    @Test
    public void managerPicksTheNewStorerBeforeTheBuiltInOne() {
        manager.getOntologyStorers().add(new ConfigurableJsonLdStorerFactory());
        for (OWLDocumentFormat format : new OWLDocumentFormat[]{new ConfigurableJsonLDDocumentFormat(), new RDFJsonLDDocumentFormat()}) {
            OWLStorerFactory first = null;
            for (OWLStorerFactory factory : manager.getOntologyStorers()) {
                if (factory.createStorer().canStoreOntology(format)) {
                    first = factory;
                    break;
                }
            }
            assertTrue("First storer for " + format + " should be ours", first instanceof ConfigurableJsonLdStorerFactory);
        }
    }

    private static JsonNode findNode(JsonNode root, String id) {
        for (JsonNode node : root.get("@graph")) {
            if (id.equals(node.path("@id").asText())) {
                return node;
            }
        }
        return null;
    }
}
