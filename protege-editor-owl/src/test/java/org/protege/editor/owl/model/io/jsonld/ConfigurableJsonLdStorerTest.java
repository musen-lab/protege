package org.protege.editor.owl.model.io.jsonld;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

public class ConfigurableJsonLdStorerTest {

    private OWLOntologyManager manager;
    private OWLOntology ontology;
    private OWLDataFactory df;
    private ObjectMapper mapper;

    @Before
    public void setUp() throws Exception {
        manager = OWLManager.createOWLOntologyManager();
        df = manager.getOWLDataFactory();
        ontology = manager.createOntology(IRI.create("http://example.org/test-ontology"));

        OWLClass personClass = df.getOWLClass(IRI.create("http://example.org/test-ontology#Person"));
        OWLNamedIndividual alice = df.getOWLNamedIndividual(IRI.create("http://example.org/test-ontology#Alice"));
        OWLDataProperty ageProp = df.getOWLDataProperty(IRI.create("http://example.org/test-ontology#hasAge"));

        manager.addAxiom(ontology, df.getOWLDeclarationAxiom(personClass));
        manager.addAxiom(ontology, df.getOWLClassAssertionAxiom(personClass, alice));
        manager.addAxiom(ontology, df.getOWLDataPropertyAssertionAxiom(ageProp, alice, df.getOWLLiteral(30)));

        mapper = new ObjectMapper();
        JsonLdPreferences.getInstance().resetToDefaults();
    }

    @After
    public void tearDown() {
        JsonLdPreferences.getInstance().resetToDefaults();
    }

    @Test
    public void testStoreOntologyWithConfigurableStorer() throws Exception {
        ConfigurableJsonLDDocumentFormat format = new ConfigurableJsonLDDocumentFormat();
        format.setDefaultPrefix("http://example.org/test-ontology#");
        format.setPrefix("ex:", "http://example.org/test-ontology#");

        ConfigurableJsonLdStorer storer = new ConfigurableJsonLdStorer(new ConfigurableJsonLDDocumentFormatFactory());
        assertTrue(storer.canStoreOntology(format));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        storer.storeOntology(ontology, baos, format);

        String jsonLdOutput = baos.toString(StandardCharsets.UTF_8.name());
        assertNotNull(jsonLdOutput);
        assertFalse(jsonLdOutput.isEmpty());

        JsonNode root = mapper.readTree(jsonLdOutput);
        assertNotNull(root);
        assertTrue("Root should have @context", root.has("@context"));
    }

    @Test
    public void testWebFriendlyPreset() throws Exception {
        JsonLdPreferences prefs = JsonLdPreferences.getInstance();
        prefs.applyPreset(JsonLdPreset.WEB_FRIENDLY);

        ConfigurableJsonLDDocumentFormat format = new ConfigurableJsonLDDocumentFormat();
        format.setDefaultPrefix("http://example.org/test-ontology#");

        ConfigurableJsonLdStorer storer = new ConfigurableJsonLdStorer(new ConfigurableJsonLDDocumentFormatFactory());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        storer.storeOntology(ontology, baos, format);

        String jsonLdOutput = baos.toString(StandardCharsets.UTF_8.name());
        JsonNode root = mapper.readTree(jsonLdOutput);

        assertTrue("Root should have @context", root.has("@context"));
        JsonNode context = root.get("@context");
        assertTrue("Context should have @vocab", context.has("@vocab"));
        assertEquals("http://example.org/test-ontology#", context.get("@vocab").asText());

        // In WEB_FRIENDLY, term generation is PROPERTIES_ONLY and terms are short local names
        assertTrue("Context should define term hasAge", context.has("hasAge"));
    }

    @Test
    public void testContextEmbeddingNone() throws Exception {
        JsonLdPreferences prefs = JsonLdPreferences.getInstance();
        prefs.setContextEmbedding(ContextEmbedding.NONE);

        ConfigurableJsonLDDocumentFormat format = new ConfigurableJsonLDDocumentFormat();
        ConfigurableJsonLdStorer storer = new ConfigurableJsonLdStorer(new ConfigurableJsonLDDocumentFormatFactory());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        storer.storeOntology(ontology, baos, format);

        String jsonLdOutput = baos.toString(StandardCharsets.UTF_8.name());
        JsonNode root = mapper.readTree(jsonLdOutput);

        assertFalse("Output should NOT contain @context when ContextEmbedding is NONE", root.has("@context"));
    }

    @Test
    public void testDiffFriendlyPreset() throws Exception {
        JsonLdPreferences prefs = JsonLdPreferences.getInstance();
        prefs.applyPreset(JsonLdPreset.DIFF_FRIENDLY);

        ConfigurableJsonLDDocumentFormat format = new ConfigurableJsonLDDocumentFormat();
        ConfigurableJsonLdStorer storer = new ConfigurableJsonLdStorer(new ConfigurableJsonLDDocumentFormatFactory());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        storer.storeOntology(ontology, baos, format);

        String jsonLdOutput = baos.toString(StandardCharsets.UTF_8.name());
        assertNotNull(jsonLdOutput);
        assertTrue(jsonLdOutput.contains("@graph"));
    }

    @Test
    public void testExpandedDocumentForm() throws Exception {
        JsonLdPreferences prefs = JsonLdPreferences.getInstance();
        prefs.applyPreset(JsonLdPreset.EXPANDED);

        ConfigurableJsonLDDocumentFormat format = new ConfigurableJsonLDDocumentFormat();
        ConfigurableJsonLdStorer storer = new ConfigurableJsonLdStorer(new ConfigurableJsonLDDocumentFormatFactory());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        storer.storeOntology(ontology, baos, format);

        String jsonLdOutput = baos.toString(StandardCharsets.UTF_8.name());
        JsonNode root = mapper.readTree(jsonLdOutput);

        assertTrue("Expanded form should be a JSON array", root.isArray());
    }

    @Test
    public void testManagerSaveOntology() throws Exception {
        JsonLdPreferences.getInstance().setContextEmbedding(ContextEmbedding.NONE);
        // Add storer factory to manager's storers
        manager.getOntologyStorers().add(new ConfigurableJsonLdStorerFactory());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ConfigurableJsonLDDocumentFormat format = new ConfigurableJsonLDDocumentFormat();
        manager.saveOntology(ontology, format, baos);
        String out = baos.toString(StandardCharsets.UTF_8.name());
        JsonNode root = mapper.readTree(out);
        assertFalse("Output should not have @context when NONE is configured via manager save", root.has("@context"));
    }
}
