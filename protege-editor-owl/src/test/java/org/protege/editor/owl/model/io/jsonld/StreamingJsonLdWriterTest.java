package org.protege.editor.owl.model.io.jsonld;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.jsonldjava.core.JsonLdConsts;
import com.github.jsonldjava.core.JsonLdApi;
import com.github.jsonldjava.core.JsonLdOptions;
import com.github.jsonldjava.core.JsonLdProcessor;
import com.github.jsonldjava.core.RDFDatasetUtils;
import com.github.jsonldjava.utils.JsonUtils;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.io.StringDocumentSource;
import org.semanticweb.owlapi.io.StringDocumentTarget;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.rio.RioNTriplesStorerFactory;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class StreamingJsonLdWriterTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private final SimpleValueFactory vf = SimpleValueFactory.getInstance();

    private static JsonLdPreferences prefs() {
        return JsonLdPreferences.ofPreset(JsonLdPreset.EXPANDED);
    }

    @Test
    public void externalMergePreservesGraphsListsCyclesLiteralsAndDeduplicatesAcrossChunks() throws Exception {
        List<Statement> statements = new ArrayList<>();
        IRI subject = vf.createIRI("urn:subject");
        IRI predicate = vf.createIRI("urn:values");
        // A single subject spans more runs than the merge fan-in, exercising multiple merge passes.
        for (int i = 0; i < 70; i++) {
            Statement statement = vf.createStatement(subject, predicate, vf.createLiteral("value " + i));
            statements.add(statement);
            statements.add(statement);
        }
        statements.add(vf.createStatement(subject, vf.createIRI("urn:unicode"), vf.createLiteral("é\n\t\"quoted\"", "fr")));
        statements.add(vf.createStatement(subject, vf.createIRI("urn:integer"),
                vf.createLiteral("01", vf.createIRI("http://www.w3.org/2001/XMLSchema#integer"))));
        statements.add(vf.createStatement(subject, predicate, vf.createBNode("cell")));
        statements.add(vf.createStatement(vf.createBNode("cell"),
                vf.createIRI("http://www.w3.org/1999/02/22-rdf-syntax-ns#first"), vf.createLiteral("item")));
        statements.add(vf.createStatement(vf.createBNode("cell"),
                vf.createIRI("http://www.w3.org/1999/02/22-rdf-syntax-ns#rest"),
                vf.createIRI("http://www.w3.org/1999/02/22-rdf-syntax-ns#nil")));
        statements.add(vf.createStatement(vf.createBNode("a"), predicate, vf.createBNode("b")));
        statements.add(vf.createStatement(vf.createBNode("b"), predicate, vf.createBNode("a")));
        statements.add(vf.createStatement(subject, predicate, vf.createLiteral("named"), vf.createIRI("urn:graph1")));
        statements.add(vf.createStatement(subject, predicate, vf.createLiteral("other"), vf.createIRI("urn:graph2")));
        statements.add(vf.createStatement(subject, predicate, vf.createLiteral("blank graph"), vf.createBNode("graph")));
        Collections.reverse(statements);

        StringWriter output = new StringWriter();
        Path parent = folder.newFolder().toPath();
        try (StreamingJsonLdWriter writer = new StreamingJsonLdWriter(parent, 1);
             JsonGenerator generator = JsonLdPostProcessor.generator(prefs(), output)) {
            for (Statement statement : statements) {
                accept(writer, statement);
            }
            writer.write(generator);
        }
        assertEmpty(parent);
        StringWriter expected = new StringWriter();
        Rio.write(statements, expected, RDFFormat.NQUADS);
        JsonLdOptions fromRdf = new JsonLdOptions();
        fromRdf.format = JsonLdConsts.APPLICATION_NQUADS;
        assertEquals(canonicalRdf(JsonLdProcessor.fromRDF(expected.toString(), fromRdf)),
                canonicalRdf(JsonUtils.fromString(output.toString())));
        for (JsonNode node : new ObjectMapper().readTree(output.toString())) {
            if (subject.stringValue().equals(node.path("@id").asText())) {
                assertEquals("70 literal values plus the list reference", 71, node.path(predicate.stringValue()).size());
            }
        }
    }

    @Test
    public void rendererMatchesTheStandardRdfWritersTriples() throws Exception {
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = manager.getOWLDataFactory();
        OWLOntology ontology = manager.createOntology(org.semanticweb.owlapi.model.IRI.create("urn:ontology"));
        org.semanticweb.owlapi.model.OWLClass cls = df.getOWLClass(org.semanticweb.owlapi.model.IRI.create("urn:Class"));
        manager.addAxiom(ontology, df.getOWLSubClassOfAxiom(cls,
                df.getOWLObjectUnionOf(cls, df.getOWLObjectSomeValuesFrom(
                        df.getOWLObjectProperty(org.semanticweb.owlapi.model.IRI.create("urn:link")), cls)))
                .getAnnotatedAxiom(Collections.singleton(df.getOWLAnnotation(df.getRDFSComment(), df.getOWLLiteral("axiom text")))));
        manager.addAxiom(ontology, df.getOWLAnnotationAssertionAxiom(df.getRDFSLabel(), cls.getIRI(), df.getOWLLiteral("plain text")));
        manager.addAxiom(ontology, df.getOWLAnnotationAssertionAxiom(df.getRDFSLabel(), cls.getIRI(), df.getOWLLiteral("texte", "fr")));
        manager.addAxiom(ontology, df.getOWLDataPropertyAssertionAxiom(
                df.getOWLDataProperty(org.semanticweb.owlapi.model.IRI.create("urn:number")),
                df.getOWLNamedIndividual(org.semanticweb.owlapi.model.IRI.create("urn:individual")),
                df.getOWLLiteral("01", df.getIntegerOWLDatatype())));
        manager.applyChange(new org.semanticweb.owlapi.model.AddOntologyAnnotation(ontology,
                df.getOWLAnnotation(df.getRDFSComment(), df.getOWLLiteral("ontology text"))));
        ConfigurableJsonLDDocumentFormat format = new ConfigurableJsonLDDocumentFormat();
        StringDocumentTarget expected = new StringDocumentTarget();
        new RioNTriplesStorerFactory().createStorer().storeOntology(ontology, expected,
                new org.semanticweb.owlapi.formats.NTriplesDocumentFormat());
        StringWriter output = new StringWriter();
        new ConfigurableJsonLdStorer(prefs()).storeOntology(ontology, output, format);
        JsonLdOptions canonical = new JsonLdOptions();
        canonical.format = JsonLdConsts.APPLICATION_NQUADS;
        // Compare raw triples: JSON-LD's @list conversion can omit redundant rdf:List typing.
        String triples = expected.toString().lines().filter(line -> !line.trim().startsWith("#"))
                .collect(Collectors.joining("\n"));
        assertEquals(new JsonLdApi(canonical).normalize(RDFDatasetUtils.parseNQuads(triples)),
                canonicalRdf(JsonUtils.fromString(output.toString())));
    }

    @Test
    public void aSpillFailureRemainsThePrimaryErrorAndRemovesItsWorkspace() throws Exception {
        Path parent = folder.newFolder().toPath();
        try {
            try (StreamingJsonLdWriter writer = new StreamingJsonLdWriter(parent, 1)) {
                Path workspace;
                try (Stream<Path> files = Files.list(parent)) {
                    workspace = files.findFirst().get();
                }
                // Make the writer's own test workspace unusable without changing permissions elsewhere.
                Files.delete(workspace);
                Files.createFile(workspace);
                accept(writer, vf.createStatement(vf.createIRI("urn:s"), vf.createIRI("urn:p"), vf.createLiteral("x")));
            }
            fail("Spilling into a non-directory must fail");
        } catch (IOException expected) {
            assertTrue("Cleanup errors must be suppressed", expected.getSuppressed().length > 0);
        }
        assertEmpty(parent);
    }

    @Test
    public void cleanupPreservesTheOriginalOutputFailure() throws Exception {
        Path parent = folder.newFolder().toPath();
        Writer failing = new Writer() {
            @Override public void write(char[] chars, int start, int count) throws IOException {
                throw new IOException("output failed");
            }
            @Override public void flush() { }
            @Override public void close() { fail("The caller's writer must not be closed"); }
        };
        try {
            try (StreamingJsonLdWriter writer = new StreamingJsonLdWriter(parent, 1);
                 JsonGenerator generator = JsonLdPostProcessor.generator(prefs(), failing)) {
                accept(writer, vf.createStatement(vf.createIRI("urn:s"), vf.createIRI("urn:p"), vf.createLiteral("x")));
                writer.write(generator);
            }
            fail("Writing must fail");
        } catch (IOException expected) {
            assertEquals("output failed", expected.getMessage());
        }
        assertEmpty(parent);
    }

    @Test
    public void cancellationPreservesTheInterruptAndCleansUpSpilledRuns() throws Exception {
        Path parent = folder.newFolder().toPath();
        try {
            try (StreamingJsonLdWriter writer = new StreamingJsonLdWriter(parent, 1)) {
                Statement statement = vf.createStatement(vf.createIRI("urn:s"), vf.createIRI("urn:p"), vf.createLiteral("x"));
                accept(writer, statement);
                Thread.currentThread().interrupt();
                try {
                    accept(writer, statement);
                    fail("Cancellation must stop rendering");
                } catch (InterruptedIOException expected) {
                    assertTrue(Thread.currentThread().isInterrupted());
                }
            }
        } finally {
            Thread.interrupted();
        }
        assertEmpty(parent);
    }

    @Test
    public void streamingPreservesMissingTypesAndDoesNotCloseTheCallersWriter() throws Exception {
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = manager.getOWLDataFactory();
        OWLOntology ontology = manager.createOntology();
        OWLDataProperty property = df.getOWLDataProperty(org.semanticweb.owlapi.model.IRI.create("urn:property"));
        manager.addAxiom(ontology, df.getOWLDataPropertyAssertionAxiom(property,
                df.getOWLNamedIndividual(org.semanticweb.owlapi.model.IRI.create("urn:individual")), df.getOWLLiteral("value")));
        for (boolean missingTypes : new boolean[]{false, true}) {
            ConfigurableJsonLDDocumentFormat format = new ConfigurableJsonLDDocumentFormat();
            format.setAddMissingTypes(missingTypes);
            boolean[] closed = {false};
            StringWriter output = new StringWriter() {
                @Override public void close() { closed[0] = true; }
            };
            new ConfigurableJsonLdStorer(prefs()).storeOntology(ontology, output, format);
            assertFalse(closed[0]);
            OWLOntology reopened = OWLManager.createOWLOntologyManager().loadOntologyFromOntologyDocument(
                    new StringDocumentSource(output.toString(), org.semanticweb.owlapi.model.IRI.create("urn:doc"),
                            new ConfigurableJsonLDDocumentFormat(), null));
            assertEquals(missingTypes, reopened.containsAxiom(df.getOWLDeclarationAxiom(property)));
        }
    }

    @Test
    public void incompatibleSettingsKeepTheExistingPipeline() {
        assertTrue(StreamingJsonLdWriter.supports(prefs()));
        JsonLdPreferences changed = prefs();
        changed.setUseNativeTypes(true);
        assertFalse(StreamingJsonLdWriter.supports(changed));
        changed = prefs();
        changed.setUseRdfType(false);
        assertFalse(StreamingJsonLdWriter.supports(changed));
        changed = prefs();
        changed.setKeyOrdering(KeyOrdering.KEYWORDS_FIRST);
        assertFalse(StreamingJsonLdWriter.supports(changed));
        changed = prefs();
        changed.setBlankNodeEmbedding(BlankNodeEmbedding.EMBED_ONCE);
        assertFalse(StreamingJsonLdWriter.supports(changed));
        changed = prefs();
        changed.setDocumentForm(DocumentForm.COMPACTED);
        assertFalse(StreamingJsonLdWriter.supports(changed));
    }

    private static void accept(StreamingJsonLdWriter writer, Statement statement) throws IOException {
        org.eclipse.rdf4j.model.Value value = statement.getObject();
        org.eclipse.rdf4j.model.Literal literal = value instanceof org.eclipse.rdf4j.model.Literal
                ? (org.eclipse.rdf4j.model.Literal) value : null;
        writer.statement(statement.getContext() == null ? "" : id(statement.getContext()),
                id(statement.getSubject()), statement.getPredicate().stringValue(), literal == null ? "I" : "L",
                literal == null ? id(value) : literal.getLabel(),
                literal == null ? "" : literal.getDatatype().stringValue(),
                literal == null ? "" : literal.getLanguage().orElse(""));
    }

    private static String id(org.eclipse.rdf4j.model.Value value) {
        return value instanceof org.eclipse.rdf4j.model.BNode
                ? "_:" + ((org.eclipse.rdf4j.model.BNode) value).getID() : value.stringValue();
    }

    private static Object canonicalRdf(Object document) throws Exception {
        JsonLdOptions options = new JsonLdOptions();
        options.format = JsonLdConsts.APPLICATION_NQUADS;
        return JsonLdProcessor.normalize(document, options);
    }

    private static void assertEmpty(Path directory) throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            assertEquals(0, files.count());
        }
    }
}
