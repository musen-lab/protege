package org.protege.editor.owl.model.io.jsonld;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.protege.editor.owl.model.DocumentFormatMapper;
import org.protege.editor.owl.model.DocumentFormatUpdater;
import org.protege.editor.owl.model.OntologyManagerFactory;
import org.protege.editor.owl.model.io.OntologyLoader;
import org.protege.editor.owl.ui.Extensions;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.PrefixDocumentFormat;
import org.semanticweb.owlapi.formats.RDFJsonDocumentFormat;
import org.semanticweb.owlapi.formats.RDFJsonLDDocumentFormat;
import org.semanticweb.owlapi.io.IRIDocumentSource;
import org.semanticweb.owlapi.model.*;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Checks the ways Protégé saves and reopens JSON-LD files: Save As, reopening a .jsonld file and saving it again,
 * and saving in the format a file was opened with (as Save and Gather Ontologies "Original" do).
 */
public class JsonLdSavePathsTest {

    private static final String NS = "http://example.org/zoo#";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private final ObjectMapper mapper = new ObjectMapper();

    private JsonLdPreferences prefs;

    private OWLOntologyManager manager;

    private OWLOntology ontology;

    @Before
    public void setUp() throws Exception {
        prefs = new JsonLdPreferences(InMemoryPreferences::new);
        manager = newManager();
        OWLDataFactory df = manager.getOWLDataFactory();
        ontology = manager.createOntology(IRI.create("http://example.org/zoo"));
        OWLClass cat = df.getOWLClass(IRI.create(NS + "Cat"));
        OWLClass animal = df.getOWLClass(IRI.create(NS + "Animal"));
        manager.addAxiom(ontology, df.getOWLSubClassOfAxiom(cat, animal));
        manager.addAxiom(ontology, df.getOWLAnnotationAssertionAxiom(cat.getIRI(),
                df.getOWLAnnotation(df.getRDFSLabel(), df.getOWLLiteral("Cat", "en"))));
    }

    /** Protégé's manager, with a JSON-LD storer that uses the test settings. */
    private OWLOntologyManager newManager() {
        OWLOntologyManager m = OntologyManagerFactory.createManager();
        m.getOntologyStorers().add(new ConfigurableJsonLdStorerFactory(prefs));
        return m;
    }

    private ConfigurableJsonLDDocumentFormat formatWithPrefixes() {
        ConfigurableJsonLDDocumentFormat format = new ConfigurableJsonLDDocumentFormat();
        format.setPrefix("ex:", NS);
        format.setPrefix("rdfs:", "http://www.w3.org/2000/01/rdf-schema#");
        format.setPrefix("owl:", "http://www.w3.org/2002/07/owl#");
        return format;
    }

    private static Set<OWLAxiom> content(OWLOntology ont) {
        Set<OWLAxiom> axioms = new HashSet<>(ont.getLogicalAxioms());
        axioms.addAll(ont.getAxioms(AxiomType.ANNOTATION_ASSERTION));
        return axioms;
    }

    /**
     * Opens a file the way Protégé does: load it with no format given (OWL API tries its parsers in turn),
     * then update its format with DocumentFormatUpdater.
     */
    private OWLOntology open(File file) throws Exception {
        OWLOntologyManager m = newManager();
        OWLOntology opened = m.loadOntologyFromOntologyDocument(new IRIDocumentSource(IRI.create(file)));
        new DocumentFormatUpdater(new DocumentFormatMapper()).updateFormat(opened);
        return opened;
    }

    @Test
    public void protegesManagerHasTheNewStorerFirstForJsonLd() {
        OWLOntologyManager protegeManager = OntologyManagerFactory.createManager();
        for (OWLDocumentFormat format : new OWLDocumentFormat[]{new ConfigurableJsonLDDocumentFormat(), new RDFJsonLDDocumentFormat()}) {
            OWLStorerFactory first = null;
            for (OWLStorerFactory factory : protegeManager.getOntologyStorers()) {
                if (factory.createStorer().canStoreOntology(format)) {
                    first = factory;
                    break;
                }
            }
            assertTrue(first instanceof ConfigurableJsonLdStorerFactory);
        }
    }

    @Test
    public void saveAsJsonLdAndReopenKeepsPrefixes() throws Exception {
        File file = folder.newFile("zoo.jsonld");
        // Save As: the chosen format with the ontology's prefixes, then save to the chosen file
        ontology.saveOntology(formatWithPrefixes(), IRI.create(file));

        OWLOntology reopened = open(file);
        OWLDocumentFormat format = reopened.getOWLOntologyManager().getOntologyFormat(reopened);
        assertTrue("Reopened JSON-LD files get Protégé's JSON-LD format", format instanceof ConfigurableJsonLDDocumentFormat);
        assertEquals(NS, format.asPrefixOWLOntologyFormat().getPrefix("ex:"));
        assertEquals(content(ontology), content(reopened));
    }

    @Test
    public void oboStylePrefixesSurviveReopening() throws Exception {
        OWLDataFactory df = manager.getOWLDataFactory();
        OWLClass process = df.getOWLClass(IRI.create("http://purl.obolibrary.org/obo/GO_0008150"));
        manager.addAxiom(ontology, df.getOWLDeclarationAxiom(process));
        ConfigurableJsonLDDocumentFormat format = formatWithPrefixes();
        format.setPrefix("GO:", "http://purl.obolibrary.org/obo/GO_");
        File file = folder.newFile("obo.jsonld");
        ontology.saveOntology(format, IRI.create(file));

        OWLOntology reopened = open(file);
        OWLDocumentFormat reopenedFormat = reopened.getOWLOntologyManager().getOntologyFormat(reopened);
        assertEquals("http://purl.obolibrary.org/obo/GO_", reopenedFormat.asPrefixOWLOntologyFormat().getPrefix("GO:"));
    }

    @Test
    public void unusedPrefixesSurviveReopening() throws Exception {
        // Diff-friendly keeps unused prefixes; a namespace ending in '_' that nothing uses must come back too
        prefs.applyPreset(JsonLdPreset.DIFF_FRIENDLY);
        prefs.setGenerateTermsFor(GenerateTermsFor.ALL_ENTITIES);
        ConfigurableJsonLDDocumentFormat format = formatWithPrefixes();
        format.setPrefix("GO:", "http://purl.obolibrary.org/obo/GO_");
        format.setPrefix("ux:", "urn:example:");
        File file = folder.newFile("unused.jsonld");
        ontology.saveOntology(format, IRI.create(file));
        assertTrue(mapper.readTree(file).get("@context").has("GO"));

        OWLOntology reopened = open(file);
        PrefixDocumentFormat reopenedFormat = reopened.getOWLOntologyManager().getOntologyFormat(reopened).asPrefixOWLOntologyFormat();
        assertEquals("http://purl.obolibrary.org/obo/GO_", reopenedFormat.getPrefix("GO:"));
        assertEquals("urn:example:", reopenedFormat.getPrefix("ux:"));
        // Short names are not taken for prefixes
        assertNull(reopenedFormat.getPrefix("Cat:"));
        assertNull(reopenedFormat.getPrefix("label:"));
    }

    @Test
    public void saveAfterReopeningKeepsPrefixes() throws Exception {
        File file = folder.newFile("zoo.jsonld");
        ontology.saveOntology(formatWithPrefixes(), IRI.create(file));

        // Reopen, then Save: same format, same file
        OWLOntology reopened = open(file);
        OWLOntologyManager m = reopened.getOWLOntologyManager();
        reopened.saveOntology(m.getOntologyFormat(reopened), m.getOntologyDocumentIRI(reopened));

        JsonNode saved = mapper.readTree(file);
        assertEquals(NS, saved.get("@context").get("ex").asText());
        assertTrue("The body still uses the prefix", saved.toString().contains("\"ex:Cat\""));
        assertEquals(content(ontology), content(open(file)));
    }

    @Test
    public void plainJsonLdFormatAlsoUsesTheNewStorer() throws Exception {
        // Gather Ontologies with "Original" format, or a JSON-LD format that was not mapped
        File file = folder.newFile("plain.jsonld");
        ontology.saveOntology(new RDFJsonLDDocumentFormat(), IRI.create(file));

        JsonNode saved = mapper.readTree(file);
        assertTrue("Written by the new storer (compacted, with a context)", saved.has("@context"));
        assertTrue(saved.get("@graph").isArray());
        assertEquals(content(ontology), content(open(file)));
    }

    @Test
    public void everyPresetsFileOpensWithoutAFormatHint() throws Exception {
        for (JsonLdPreset preset : JsonLdPreset.values()) {
            prefs.applyPreset(preset);
            File file = folder.newFile("zoo-" + preset + ".jsonld");
            ontology.saveOntology(formatWithPrefixes(), IRI.create(file));
            assertEquals("Preset " + preset, content(ontology), content(open(file)));
        }
    }

    @Test
    public void fileOpenLoadsEveryPresetsFile() throws Exception {
        // The manager Protégé's File > Open really uses (OntologyLoader), not OntologyManagerFactory's
        for (JsonLdPreset preset : JsonLdPreset.values()) {
            prefs.applyPreset(preset);
            File file = folder.newFile("open-" + preset + ".jsonld");
            ontology.saveOntology(formatWithPrefixes(), IRI.create(file));
            OWLOntologyManager loadingManager = OntologyLoader.createInterceptingManager(() -> {});
            OWLOntology opened = loadingManager.loadOntologyFromOntologyDocument(new IRIDocumentSource(IRI.create(file)));
            assertEquals("Preset " + preset, content(ontology), content(opened));
        }
    }

    @Test
    public void jsonLdSupportCanBeAddedToAnyManagerOnce() throws Exception {
        OWLOntologyManager plain = OWLManager.createOWLOntologyManager();
        OntologyManagerFactory.addJsonLdSupport(plain);
        OntologyManagerFactory.addJsonLdSupport(plain);
        int storers = 0;
        for (OWLStorerFactory factory : plain.getOntologyStorers()) {
            if (factory instanceof ConfigurableJsonLdStorerFactory) {
                storers++;
            }
        }
        assertEquals(1, storers);

        File file = folder.newFile("any.jsonld");
        ontology.saveOntology(formatWithPrefixes(), IRI.create(file));
        assertEquals(content(ontology), content(plain.loadOntologyFromOntologyDocument(new IRIDocumentSource(IRI.create(file)))));
    }

    @Test
    public void rdfJsonFilesStillOpen() throws Exception {
        File file = folder.newFile("zoo.rj");
        ontology.saveOntology(new RDFJsonDocumentFormat(), IRI.create(file));
        OWLOntology opened = open(file);
        assertTrue(opened.getOWLOntologyManager().getOntologyFormat(opened) instanceof RDFJsonDocumentFormat);
        assertEquals(content(ontology), content(opened));
    }

    @Test
    public void reopeningKeepsTheAddMissingTypesSetting() {
        RDFJsonLDDocumentFormat loaded = new RDFJsonLDDocumentFormat();
        loaded.setAddMissingTypes(false);
        OWLDocumentFormat mapped = new DocumentFormatMapper().mapFormat(loaded);
        assertTrue(mapped instanceof ConfigurableJsonLDDocumentFormat);
        assertFalse(mapped.isAddMissingTypes());
    }

    @Test
    public void protegesFormatIsNotMappedAgain() {
        ConfigurableJsonLDDocumentFormat format = formatWithPrefixes();
        assertSame(format, new DocumentFormatMapper().mapFormat(format));
    }

    @Test
    public void jsonLdFilesGetTheJsonLdExtension() {
        assertEquals(Collections.singletonList(".jsonld"), Extensions.getExtensions(new ConfigurableJsonLDDocumentFormat()));
        assertEquals(Collections.singletonList(".jsonld"), Extensions.getExtensions(new RDFJsonLDDocumentFormat()));
    }
}
