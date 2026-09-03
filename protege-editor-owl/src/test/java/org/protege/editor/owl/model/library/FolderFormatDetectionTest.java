package org.protege.editor.owl.model.library;

import org.junit.Before;
import org.junit.Test;
import org.protege.editor.owl.model.library.folder.FolderGroupManager;
import org.protege.xmlcatalog.CatalogUtilities;
import org.protege.xmlcatalog.XMLCatalog;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * T1 (Import Management): a local copy of an imported ontology must be auto-detected
 * by the folder scan that builds catalog-v001.xml, whatever OWL serialization the
 * copy uses. Each test places a single fixture in an empty folder, generates the
 * catalog for that folder, and asserts the catalog redirects the ontology IRI to
 * the local file.
 *
 * These tests specify the intended behavior. At the time of writing only the
 * RDF/XML-with-xml:base case passes: FolderGroupManager extracts ontology IRIs
 * solely via XmlBaseAlgorithm, which reads the xml:base attribute off the first
 * XML element and silently gives up on anything else.
 *
 * The cases that reproduce the defect (T1.1) are marked
 * {@code @Test(expected = AssertionError.class)}: they pass while the defect is
 * present, which makes them the executable reproduction, and they will fail the
 * moment detection is fixed. The T1.3 fix removes those annotations, turning the
 * same tests into the permanent regression suite.
 */
public class FolderFormatDetectionTest {

    public static final File SOURCE_DIR = new File("src/test/resources/ontologies/formats");
    public static final File TEST_ROOT = new File("target/format-detection.test");

    @Before
    public void cleanTestRoot() {
        removeDirectory(TEST_ROOT);
        TEST_ROOT.mkdirs();
    }

    @Test
    public void detectsRdfXmlWithXmlBase() throws IOException {
        assertLocalCopyDetected("toppings-rdfxml-xmlbase.owl",
                                "http://import-test.invalid/formats/toppings-rdfxml-xmlbase");
    }

    @Test(expected = AssertionError.class)
    public void detectsRdfXmlWithoutXmlBase() throws IOException {
        assertLocalCopyDetected("toppings-rdfxml-no-xmlbase.owl",
                                "http://import-test.invalid/formats/toppings-rdfxml-no-xmlbase");
    }

    @Test(expected = AssertionError.class)
    public void detectsTurtle() throws IOException {
        assertLocalCopyDetected("toppings.ttl",
                                "http://import-test.invalid/formats/toppings-ttl");
    }

    @Test(expected = AssertionError.class)
    public void detectsFunctionalSyntax() throws IOException {
        assertLocalCopyDetected("toppings.ofn",
                                "http://import-test.invalid/formats/toppings-ofn");
    }

    @Test(expected = AssertionError.class)
    public void detectsManchesterSyntax() throws IOException {
        assertLocalCopyDetected("toppings.omn",
                                "http://import-test.invalid/formats/toppings-omn");
    }

    @Test(expected = AssertionError.class)
    public void detectsOboFormat() throws IOException {
        // The OBO parser derives the ontology IRI from the "ontology:" tag
        // using the OBO Foundry convention.
        assertLocalCopyDetected("toppings.obo",
                                "http://purl.obolibrary.org/obo/t1toppings.owl");
    }

    @Test(expected = AssertionError.class)
    public void detectsOwlXmlWithoutXmlBase() throws IOException {
        assertLocalCopyDetected("toppings.owx",
                                "http://import-test.invalid/formats/toppings-owx");
    }

    private void assertLocalCopyDetected(String fixtureName, String ontologyIri) throws IOException {
        File folder = new File(TEST_ROOT, fixtureName.replace('.', '-'));
        folder.mkdirs();
        File localCopy = new File(folder, fixtureName);
        Files.copy(new File(SOURCE_DIR, fixtureName).toPath(), localCopy.toPath(),
                   StandardCopyOption.REPLACE_EXISTING);

        OntologyCatalogManager catalogManager
                = new OntologyCatalogManager(Collections.singletonList(new FolderGroupManager()));
        XMLCatalog catalog = catalogManager.ensureCatalogExists(folder);

        URI redirect = CatalogUtilities.getRedirect(URI.create(ontologyIri), catalog);
        assertNotNull("Local copy " + fixtureName + " was not auto-detected as " + ontologyIri, redirect);
        assertEquals(localCopy.toURI(), redirect);
    }

    private static void removeDirectory(File dir) {
        File[] children = dir.listFiles();
        if (children != null) {
            for (File child : children) {
                removeDirectory(child);
            }
        }
        dir.delete();
    }
}
