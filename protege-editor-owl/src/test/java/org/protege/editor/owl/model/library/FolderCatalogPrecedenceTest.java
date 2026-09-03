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
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Declared IRIs take precedence over derived ones (review finding F3). An OBO
 * file's IRIs are derived by convention, and the OWL rendering of the same
 * ontology, when present, declares the same IRI. Without precedence the catalog
 * would call that a duplicate and resolve neither, breaking OBO Foundry style
 * folders that ship {@code x.owl} next to {@code x.obo}. Precedence must also
 * survive later catalog updates, where earlier claims come back from the catalog
 * file rather than from a fresh scan, so provenance is persisted per entry.
 */
public class FolderCatalogPrecedenceTest {

    private static final File TEST_ROOT = new File("target/catalog-precedence.test");

    private static final String OWL_IRI = "http://purl.obolibrary.org/obo/shared.owl";
    private static final String OBO_URL = "http://purl.obolibrary.org/obo/shared.obo";

    @Before
    public void cleanTestRoot() throws IOException {
        if (TEST_ROOT.exists()) {
            Files.walk(TEST_ROOT.toPath()).sorted(java.util.Comparator.reverseOrder())
                 .forEach(p -> p.toFile().delete());
        }
        TEST_ROOT.mkdirs();
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
    public void declaredOwlFileWinsWhenItSortsBeforeTheOboFile() throws IOException {
        File folder = new File(TEST_ROOT, "owl-first");
        File owl = writeOwl(folder, "a-shared.owl");
        writeObo(folder, "z-shared.obo");
        assertEquals(owl.toURI(), redirect(scan(folder), OWL_IRI));
    }

    @Test
    public void declaredOwlFileWinsWhenItSortsAfterTheOboFile() throws IOException {
        File folder = new File(TEST_ROOT, "obo-first");
        writeObo(folder, "a-shared.obo");
        File owl = writeOwl(folder, "z-shared.owl");
        assertEquals(owl.toURI(), redirect(scan(folder), OWL_IRI));
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
