package org.protege.editor.owl.model.library;

import org.junit.Before;
import org.junit.Test;
import org.protege.editor.owl.model.library.folder.FolderGroupManager;
import org.protege.xmlcatalog.CatalogUtilities;
import org.protege.xmlcatalog.XMLCatalog;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * When {@code x.owl} and {@code x.obo} describe the same ontology, both give the
 * same IRI: the OWL file states it, the OBO file's is built from its
 * {@code ontology:} line. The OWL file must win, or the catalog would call it a
 * duplicate and resolve neither. This must still hold on later updates, when
 * entries come back from the catalog file instead of a fresh scan, so each
 * entry records where its IRI came from.
 */
public class FolderCatalogPrecedenceTest {

    private static final File TEST_ROOT = new File("target/catalog-precedence.test");

    private static final String OWL_IRI = "http://purl.obolibrary.org/obo/shared.owl";
    private static final String OBO_URL = "http://purl.obolibrary.org/obo/shared.obo";

    @Before
    public void cleanTestRoot() throws IOException {
        if (TEST_ROOT.exists()) {
            try (Stream<Path> paths = Files.walk(TEST_ROOT.toPath())) {
                for (Path p : paths.sorted(Comparator.reverseOrder()).collect(Collectors.toList())) {
                    Files.delete(p);
                }
            }
        }
        assertTrue("Could not create " + TEST_ROOT, TEST_ROOT.mkdirs());
    }

    @Test
    public void declaredOwlFileWinsOverDerivedOboInTheSameScan() throws IOException {
        File folder = new File(TEST_ROOT, "pair");
        File owl = writeOwl(folder, "shared.owl");
        File obo = writeObo(folder, "shared.obo");

        XMLCatalog catalog = scan(folder);

        assertEquals(owl.toURI(), redirect(catalog, OWL_IRI));
        assertEquals(obo.toURI(), redirect(catalog, OBO_URL));
    }

    @Test
    public void declaredOwlFileWinsWhateverTheFileNames() throws IOException {
        // File.listFiles() order is not controlled by names, so this cannot force a
        // scan order. It shows the outcome does not depend on names either: the
        // winner is chosen from the collected claims when entries are written.
        File first = new File(TEST_ROOT, "names-1");
        File owlA = writeOwl(first, "a-shared.owl");
        writeObo(first, "z-shared.obo");
        assertEquals(owlA.toURI(), redirect(scan(first), OWL_IRI));

        File second = new File(TEST_ROOT, "names-2");
        writeObo(second, "a-shared.obo");
        File owlZ = writeOwl(second, "z-shared.owl");
        assertEquals(owlZ.toURI(), redirect(scan(second), OWL_IRI));
    }

    @Test
    public void declaredOwlFileAddedLaterWinsOverAnOboEntryRetainedFromAnEarlierScan() throws IOException {
        File folder = new File(TEST_ROOT, "later");
        File obo = writeObo(folder, "shared.obo");
        // Make the OBO file look older than the catalog about to be written, so
        // the next update retains its entry from the catalog instead of rescanning.
        obo.setLastModified(System.currentTimeMillis() - 60_000);
        XMLCatalog first = scan(folder);
        assertEquals("Alone, the OBO file answers the derived .owl IRI", obo.toURI(), redirect(first, OWL_IRI));

        File owl = writeOwl(folder, "shared.owl");
        XMLCatalog second = scan(folder);

        assertEquals("A newly added declared file must beat the retained derived claim",
                     owl.toURI(), redirect(second, OWL_IRI));
        assertEquals(obo.toURI(), redirect(second, OBO_URL));
    }

    @Test
    public void twoDeclaredClaimantsRemainADuplicate() throws IOException {
        File folder = new File(TEST_ROOT, "two-declared");
        writeOwl(folder, "one.owl");
        writeOwl(folder, "two.owl");
        assertNull(redirect(scan(folder), OWL_IRI));
    }

    @Test
    public void twoDerivedClaimantsRemainADuplicate() throws IOException {
        File folder = new File(TEST_ROOT, "two-derived");
        writeObo(folder, "one.obo");
        writeObo(folder, "two.obo");
        assertNull(redirect(scan(folder), OWL_IRI));
    }

    private static File writeOwl(File folder, String name) throws IOException {
        folder.mkdirs();
        File f = new File(folder, name);
        Files.write(f.toPath(), ("<?xml version=\"1.0\"?>\n"
                + "<rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\""
                + " xmlns:owl=\"http://www.w3.org/2002/07/owl#\" xml:base=\"" + OWL_IRI + "\">\n"
                + "  <owl:Ontology rdf:about=\"" + OWL_IRI + "\"/>\n</rdf:RDF>\n").getBytes(StandardCharsets.UTF_8));
        return f;
    }

    private static File writeObo(File folder, String name) throws IOException {
        folder.mkdirs();
        File f = new File(folder, name);
        Files.write(f.toPath(), "format-version: 1.2\nontology: shared\n\n[Term]\nid: S:1\nname: a\n"
                .getBytes(StandardCharsets.UTF_8));
        return f;
    }

    private static XMLCatalog scan(File folder) throws IOException {
        OntologyCatalogManager manager
                = new OntologyCatalogManager(Collections.singletonList(new FolderGroupManager()));
        return manager.ensureCatalogExists(folder);
    }

    private static URI redirect(XMLCatalog catalog, String iri) {
        return CatalogUtilities.getRedirect(URI.create(iri), catalog);
    }
}
