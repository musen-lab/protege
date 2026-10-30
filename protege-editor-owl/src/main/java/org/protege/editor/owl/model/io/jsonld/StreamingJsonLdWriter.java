package org.protege.editor.owl.model.io.jsonld;

import com.fasterxml.jackson.core.JsonGenerator;
import org.semanticweb.owlapi.io.RDFLiteral;
import org.semanticweb.owlapi.io.RDFNode;
import org.semanticweb.owlapi.io.RDFResource;
import org.semanticweb.owlapi.io.RDFTriple;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.rdf.RDFRendererBase;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Writes expanded JSON-LD without collecting the complete RDF model or JSON document.
 * Statements are sorted and deduplicated using temporary disk runs. Blank nodes, including
 * RDF list cells, remain separate nodes; literals retain their RDF lexical forms.
 * The renderer and the already-loaded ontology can still allocate memory independently.
 */
final class StreamingJsonLdWriter implements Closeable {

    private static final int CHUNK_CHARACTERS = 2 * 1024 * 1024;
    private static final int MERGE_FAN_IN = 16;
    private static final String XSD_STRING = "http://www.w3.org/2001/XMLSchema#string";

    private final Path directory;
    private final int chunkCharacters;
    private final List<String> buffer = new ArrayList<>();
    private final List<Path> runs = new ArrayList<>();
    private long bufferedCharacters;

    StreamingJsonLdWriter() throws IOException {
        this(null, CHUNK_CHARACTERS);
    }

    /** A smaller chunk budget and a private parent directory allow spill/cleanup testing. */
    StreamingJsonLdWriter(Path parent, int chunkCharacters) throws IOException {
        if (chunkCharacters <= 0) {
            throw new IllegalArgumentException("The chunk budget must be positive");
        }
        this.chunkCharacters = chunkCharacters;
        directory = parent == null ? Files.createTempDirectory("protege-jsonld-stream-")
                : Files.createTempDirectory(parent, "protege-jsonld-stream-");
    }

    static boolean supports(JsonLdPreferences prefs) {
        return prefs.getDocumentForm() == DocumentForm.EXPANDED
                && prefs.getBlankNodeEmbedding() == BlankNodeEmbedding.REFERENCE
                && prefs.getKeyOrdering() == KeyOrdering.NONE
                && !prefs.isUseNativeTypes()
                && prefs.isUseRdfType();
    }

    void render(OWLOntology ontology, OWLDocumentFormat format, JsonGenerator output) throws IOException {
        checkCancelled();
        new Renderer(ontology, format).render();
        write(output);
    }

    private void triple(RDFTriple triple) throws IOException {
        RDFNode object = triple.getObject();
        if (object instanceof RDFLiteral) {
            RDFLiteral literal = (RDFLiteral) object;
            statement("", id(triple.getSubject()), triple.getPredicate().getIRI().toString(), "L",
                    literal.getLexicalValue(), literal.isPlainLiteral() ? XSD_STRING : literal.getDatatype().toString(),
                    literal.getLang());
        } else {
            statement("", id(triple.getSubject()), triple.getPredicate().getIRI().toString(), "I",
                    id((RDFResource) object), "", "");
        }
    }

    /** Receives a single RDF statement; graph identifiers are empty for OWL API ontology rendering. */
    void statement(String graph, String subject, String predicate, String kind, String value,
                   String datatype, String language) throws IOException {
        checkCancelled();
        String line = encode(graph) + '\t' + encode(subject) + '\t' + encode(predicate) + '\t'
                + kind + '\t' + encode(value) + '\t' + encode(datatype) + '\t' + encode(language);
        buffer.add(line);
        // Account for per-string overhead as well as characters. One RDF term can exceed the budget.
        bufferedCharacters += line.length() + 64L;
        if (bufferedCharacters >= chunkCharacters) {
            spill();
        }
    }

    private static String id(RDFResource value) {
        return value.isAnonymous() ? "_:" + value.getNodeIDValue() : value.getIRI().toString();
    }

    /** Uses only OWL API's exported types, without RioRenderer's global rendered-statement set. */
    private final class Renderer extends RDFRendererBase {
        Renderer(OWLOntology ontology, OWLDocumentFormat format) {
            super(ontology, format);
        }

        @Override
        public void render(RDFResource root, boolean rootNode) throws IOException {
            Set<RDFResource> visited = new HashSet<>();
            ArrayDeque<RDFResource> work = new ArrayDeque<>();
            visited.add(root);
            work.add(root);
            while (!work.isEmpty()) {
                checkCancelled();
                for (RDFTriple triple : getRDFGraph().getTriplesForSubject(work.removeFirst())) {
                    triple(triple);
                    if (triple.getObject() instanceof RDFResource) {
                        RDFResource next = (RDFResource) triple.getObject();
                        if (!getRDFGraph().getTriplesForSubject(next).isEmpty() && visited.add(next)) {
                            work.add(next);
                        }
                    }
                }
            }
        }

        @Override protected void beginDocument() { }
        @Override protected void endDocument() { }
        @Override protected void writeAnnotationPropertyComment(OWLAnnotationProperty property) { }
        @Override protected void writeDataPropertyComment(OWLDataProperty property) { }
        @Override protected void writeObjectPropertyComment(OWLObjectProperty property) { }
        @Override protected void writeClassComment(OWLClass cls) { }
        @Override protected void writeDatatypeComment(OWLDatatype datatype) { }
        @Override protected void writeIndividualComments(OWLNamedIndividual individual) { }
        @Override protected void writeBanner(String banner) { }
    }

    private static String encode(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private void spill() throws IOException {
        checkCancelled();
        if (buffer.isEmpty()) {
            return;
        }
        Collections.sort(buffer);
        Path run = Files.createTempFile(directory, "run-", ".txt");
        try (BufferedWriter writer = Files.newBufferedWriter(run, StandardCharsets.UTF_8)) {
            String previous = null;
            for (String line : buffer) {
                checkCancelled();
                if (!line.equals(previous)) {
                    writer.write(line);
                    writer.newLine();
                    previous = line;
                }
            }
        }
        runs.add(run);
        buffer.clear();
        bufferedCharacters = 0;
    }

    private Path sortedStatements() throws IOException {
        spill();
        while (runs.size() > 1) {
            List<Path> merged = new ArrayList<>();
            for (int start = 0; start < runs.size(); start += MERGE_FAN_IN) {
                List<Path> inputs = runs.subList(start, Math.min(start + MERGE_FAN_IN, runs.size()));
                Path output = Files.createTempFile(directory, "merge-", ".txt");
                merge(inputs, output);
                merged.add(output);
                for (Path input : inputs) {
                    Files.delete(input);
                }
            }
            runs.clear();
            runs.addAll(merged);
        }
        return runs.isEmpty() ? null : runs.get(0);
    }

    private static void merge(List<Path> inputs, Path output) throws IOException {
        checkCancelled();
        try (Readers readers = new Readers();
             BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            PriorityQueue<Cursor> queue = new PriorityQueue<>(Comparator.comparing(cursor -> cursor.line));
            for (Path input : inputs) {
                Cursor cursor = readers.open(input);
                if (cursor.advance()) {
                    queue.add(cursor);
                }
            }
            String previous = null;
            while (!queue.isEmpty()) {
                checkCancelled();
                Cursor cursor = queue.remove();
                if (!cursor.line.equals(previous)) {
                    writer.write(cursor.line);
                    writer.newLine();
                    previous = cursor.line;
                }
                if (cursor.advance()) {
                    queue.add(cursor);
                }
            }
        }
    }

    /** Writes arrays incrementally, including subjects with more values than a sort chunk. */
    void write(JsonGenerator output) throws IOException {
        Path sorted = sortedStatements();
        output.writeStartArray();
        if (sorted != null) {
            try (BufferedReader reader = Files.newBufferedReader(sorted, StandardCharsets.UTF_8)) {
                String graph = null;
                String subject = null;
                String predicate = null;
                String line;
                while ((line = reader.readLine()) != null) {
                    checkCancelled();
                    String[] fields = line.split("\t", -1);
                    String nextGraph = decode(fields[0]);
                    String nextSubject = decode(fields[1]);
                    String nextPredicate = decode(fields[2]);
                    if (!nextGraph.equals(graph)) {
                        if (subject != null) {
                            output.writeEndArray();
                            output.writeEndObject();
                        }
                        if (graph != null && !graph.isEmpty()) {
                            output.writeEndArray();
                            output.writeEndObject();
                        }
                        graph = nextGraph;
                        subject = null;
                        predicate = null;
                        if (!graph.isEmpty()) {
                            output.writeStartObject();
                            output.writeStringField("@id", graph);
                            output.writeArrayFieldStart("@graph");
                        }
                    }
                    if (!nextSubject.equals(subject)) {
                        if (subject != null) {
                            output.writeEndArray();
                            output.writeEndObject();
                        }
                        subject = nextSubject;
                        predicate = null;
                        output.writeStartObject();
                        output.writeStringField("@id", subject);
                    }
                    if (!nextPredicate.equals(predicate)) {
                        if (predicate != null) {
                            output.writeEndArray();
                        }
                        predicate = nextPredicate;
                        output.writeArrayFieldStart(predicate);
                    }
                    output.writeStartObject();
                    if ("I".equals(fields[3])) {
                        output.writeStringField("@id", decode(fields[4]));
                    } else {
                        output.writeStringField("@value", decode(fields[4]));
                        String language = decode(fields[6]);
                        String datatype = decode(fields[5]);
                        if (!language.isEmpty()) {
                            output.writeStringField("@language", language);
                        } else if (!XSD_STRING.equals(datatype)) {
                            output.writeStringField("@type", datatype);
                        }
                    }
                    output.writeEndObject();
                }
                if (subject != null) {
                    output.writeEndArray();
                    output.writeEndObject();
                }
                if (graph != null && !graph.isEmpty()) {
                    output.writeEndArray();
                    output.writeEndObject();
                }
            }
        }
        output.writeEndArray();
        output.flush();
    }

    private static void checkCancelled() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedIOException("JSON-LD streaming was cancelled");
        }
    }

    @Override
    public void close() throws IOException {
        buffer.clear();
        runs.clear();
        IOException failure = null;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory)) {
            for (Path file : files) {
                try {
                    Files.deleteIfExists(file);
                } catch (IOException e) {
                    if (failure == null) {
                        failure = e;
                    } else {
                        failure.addSuppressed(e);
                    }
                }
            }
        } catch (IOException e) {
            if (failure == null) {
                failure = e;
            } else {
                failure.addSuppressed(e);
            }
        }
        try {
            Files.deleteIfExists(directory);
        } catch (IOException e) {
            if (failure == null) {
                failure = e;
            } else {
                failure.addSuppressed(e);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static final class Cursor {
        private final BufferedReader reader;
        private String line;

        Cursor(BufferedReader reader) {
            this.reader = reader;
        }

        boolean advance() throws IOException {
            line = reader.readLine();
            return line != null;
        }
    }

    private static final class Readers implements Closeable {
        private final List<BufferedReader> readers = new ArrayList<>();

        Cursor open(Path path) throws IOException {
            BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
            readers.add(reader);
            return new Cursor(reader);
        }

        @Override
        public void close() throws IOException {
            IOException failure = null;
            for (BufferedReader reader : readers) {
                try {
                    reader.close();
                } catch (IOException e) {
                    if (failure == null) {
                        failure = e;
                    } else {
                        failure.addSuppressed(e);
                    }
                }
            }
            if (failure != null) {
                throw failure;
            }
        }
    }
}
