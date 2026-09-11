package org.protege.editor.owl.model.declaration;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.FunctionalSyntaxDocumentFormat;
import org.semanticweb.owlapi.formats.OWLXMLDocumentFormat;
import org.semanticweb.owlapi.formats.RDFXMLDocumentFormat;
import org.semanticweb.owlapi.formats.TurtleDocumentFormat;
import org.semanticweb.owlapi.io.StringDocumentSource;
import org.semanticweb.owlapi.io.StringDocumentTarget;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.MissingImportHandlingStrategy;
import org.semanticweb.owlapi.model.OWLDeclarationAxiom;
import org.semanticweb.owlapi.model.OWLDocumentFormat;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyLoaderConfiguration;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Provides test data and methods for checking the declarations written by default when saving.
 *
 * <p>For each test ontology, the test creates an RDF/XML, Turtle, OWL/XML, and Functional Syntax
 * document. It loads the declarations from each new document and from its expected document, then
 * compares them. Only declarations are compared because this setting affects declarations, not
 * details such as prefix order or generated comments.
 */
public class DeclarationBaseline {

    /** Ontologies whose saved declarations are compared with the expected documents. */
    public static final List<String> TEST_ONTOLOGIES = Collections.unmodifiableList(Arrays.asList(
            "amino-acid.owl",
            "pizza.owl",
            "simpleLoop.owl",
            "twoEq.owl",
            "twoParents.owl"));

    public static final Path TEST_ONTOLOGY_DIR = Paths.get("src/test/resources/declarations/corpus");

    public static final Path EXPECTED_OUTPUT_DIR = Paths.get("src/test/resources/declarations/baseline");

    /** The four formats changed by the automatic declaration setting. */
    public enum Format {

        RDF_XML("rdf") {
            @Override
            OWLDocumentFormat newFormat() {
                return new RDFXMLDocumentFormat();
            }
        },

        TURTLE("ttl") {
            @Override
            OWLDocumentFormat newFormat() {
                return new TurtleDocumentFormat();
            }
        },

        OWL_XML("owx") {
            @Override
            OWLDocumentFormat newFormat() {
                return new OWLXMLDocumentFormat();
            }
        },

        FUNCTIONAL_SYNTAX("ofn") {
            @Override
            OWLDocumentFormat newFormat() {
                return new FunctionalSyntaxDocumentFormat();
            }
        };

        private final String extension;

        Format(String extension) {
            this.extension = extension;
        }

        abstract OWLDocumentFormat newFormat();

        public String getExtension() {
            return extension;
        }
    }

    public static OWLOntology loadTestOntology(String fileName) throws OWLOntologyCreationException {
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        manager.setOntologyLoaderConfiguration(silentAboutMissingImports());
        return manager.loadOntologyFromOntologyDocument(TEST_ONTOLOGY_DIR.resolve(fileName).toFile());
    }

    /** Saves an ontology using a fresh format, which includes automatic declarations by default. */
    public static String saveToString(OWLOntology ontology, Format format) throws OWLOntologyStorageException {
        StringDocumentTarget target = new StringDocumentTarget();
        ontology.saveOntology(format.newFormat(), target);
        return target.toString();
    }

    /**
     * Loads a saved document and returns its declarations in a consistent order so failures are
     * easy to read.
     */
    public static SortedSet<String> declarationsOf(String document) throws OWLOntologyCreationException {
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        manager.setOntologyLoaderConfiguration(silentAboutMissingImports());
        OWLOntology parsed = manager.loadOntologyFromOntologyDocument(new StringDocumentSource(document));
        SortedSet<String> declarations = new TreeSet<>();
        for (OWLDeclarationAxiom axiom : parsed.getAxioms(AxiomType.DECLARATION)) {
            declarations.add(describe(axiom));
        }
        return declarations;
    }

    /**
     * Describes a declaration by its entity type and IRI.
     */
    public static String describe(OWLDeclarationAxiom axiom) {
        return axiom.getEntity().getEntityType().getName() + " " + axiom.getEntity().getIRI();
    }

    public static SortedSet<String> expectedDeclarations(String ontologyFileName, Format format)
            throws OWLOntologyCreationException, IOException {
        return declarationsOf(readExpectedDocument(ontologyFileName, format));
    }

    public static Path expectedDocumentPath(String ontologyFileName, Format format) {
        String stem = ontologyFileName.replaceAll("\\.owl$", "");
        return EXPECTED_OUTPUT_DIR.resolve(stem + "." + format.getExtension());
    }

    public static String readExpectedDocument(String ontologyFileName, Format format) throws IOException {
        return new String(Files.readAllBytes(expectedDocumentPath(ontologyFileName, format)),
                StandardCharsets.UTF_8);
    }

    private static OWLOntologyLoaderConfiguration silentAboutMissingImports() {
        return new OWLOntologyLoaderConfiguration()
                .setMissingImportHandlingStrategy(MissingImportHandlingStrategy.SILENT);
    }

    /**
     * Rewrites every expected document from the test ontologies.
     *
     * <p>Run this only after confirming that a declaration change is intentional. See
     * {@code src/test/resources/declarations/README.md} for the procedure.
     */
    public static void main(String[] args) throws Exception {
        Files.createDirectories(EXPECTED_OUTPUT_DIR);
        List<String> written = new ArrayList<>();
        for (String ontologyFileName : TEST_ONTOLOGIES) {
            OWLOntology ontology = loadTestOntology(ontologyFileName);
            for (Format format : Format.values()) {
                Path target = expectedDocumentPath(ontologyFileName, format);
                Files.write(target, saveToString(ontology, format).getBytes(StandardCharsets.UTF_8));
                written.add(target.toString());
            }
        }
        for (String path : written) {
            System.out.println("wrote " + path);
        }
        System.out.println("Updated " + written.size() + " expected documents.");
    }

    private DeclarationBaseline() {
    }

    static {
        // Stop if the test ontology directory is missing. Otherwise the tests could pass without
        // checking any ontology.
        File testOntologyDir = TEST_ONTOLOGY_DIR.toFile();
        if (!testOntologyDir.isDirectory()) {
            throw new IllegalStateException("Test ontology directory not found: "
                    + testOntologyDir.getAbsolutePath());
        }
    }
}
