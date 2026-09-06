package org.protege.editor.owl.model.library;

import org.junit.Before;
import org.junit.Test;
import org.protege.editor.owl.model.library.folder.FolderGroupManager;
import org.protege.xmlcatalog.CatalogUtilities;
import org.protege.xmlcatalog.XMLCatalog;
import org.protege.xmlcatalog.entry.GroupEntry;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * A local copy of an imported ontology must be found by the folder scan that
 * builds catalog-v001.xml, whatever format the copy is in. Each test puts one
 * file in an empty folder, builds the catalog, and checks that the catalog points
 * the ontology IRI at that file.
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

    @Test
    public void detectsRdfXmlWithoutXmlBase() throws IOException {
        assertLocalCopyDetected("toppings-rdfxml-no-xmlbase.owl",
                                "http://import-test.invalid/formats/toppings-rdfxml-no-xmlbase");
    }

    @Test
    public void detectsTurtle() throws IOException {
        assertLocalCopyDetected("toppings.ttl",
                                "http://import-test.invalid/formats/toppings-ttl");
    }

    @Test
    public void detectsFunctionalSyntax() throws IOException {
        assertLocalCopyDetected("toppings.ofn",
                                "http://import-test.invalid/formats/toppings-ofn");
    }

    @Test
    public void detectsManchesterSyntax() throws IOException {
        assertLocalCopyDetected("toppings.omn",
                                "http://import-test.invalid/formats/toppings-omn");
    }

    @Test
    public void detectsOboFormat() throws IOException {
        // The OBO parser derives the ontology IRI from the "ontology:" tag using
        // the OBO Foundry convention. The .obo document URL is also expected,
        // because OBO import lines conventionally reference the document.
        assertLocalCopyDetected("toppings.obo",
                                "http://purl.obolibrary.org/obo/t1toppings.owl",
                                "http://purl.obolibrary.org/obo/t1toppings.obo");
    }

    @Test
    public void detectsOboFormatWithSlashedId() throws IOException {
        // OBO Foundry subset ontologies use slashed ids (e.g. go/subsets/goslim_generic).
        assertLocalCopyDetected("toppings-subset.obo",
                                "http://purl.obolibrary.org/obo/t1toppings/subsets/basic.owl",
                                "http://purl.obolibrary.org/obo/t1toppings/subsets/basic.obo");
    }

    /*
     * Version IRIs (decided 2026-08-31, #11): a versioned local copy must also be
     * importable by its owl:versionIRI, in every supported serialization. The
     * fixtures mirror the OWL API's own serializer output line-for-line, which is
     * the population these files overwhelmingly come from.
     */

    @Test
    public void detectsVersionIriInRdfXml() throws IOException {
        assertLocalCopyDetected("versioned-rdfxml.owl",
                                "http://import-test.invalid/versioned/rdfxml",
                                "http://import-test.invalid/versioned/rdfxml/1.0");
    }

    @Test
    public void detectsVersionIriInOwlXml() throws IOException {
        assertLocalCopyDetected("versioned.owx",
                                "http://import-test.invalid/versioned/owx",
                                "http://import-test.invalid/versioned/owx/1.0");
    }

    @Test
    public void detectsVersionIriInTurtle() throws IOException {
        // The OWL API's Turtle writer puts owl:versionIRI on a continuation line.
        assertLocalCopyDetected("versioned.ttl",
                                "http://import-test.invalid/versioned/ttl",
                                "http://import-test.invalid/versioned/ttl/1.0");
    }

    @Test
    public void detectsVersionIriInFunctionalSyntax() throws IOException {
        // The OWL API's functional writer puts the version IRI alone on the next line.
        assertLocalCopyDetected("versioned.ofn",
                                "http://import-test.invalid/versioned/ofn",
                                "http://import-test.invalid/versioned/ofn/1.0");
    }

    @Test
    public void detectsVersionIriInManchesterSyntax() throws IOException {
        assertLocalCopyDetected("versioned.omn",
                                "http://import-test.invalid/versioned/omn",
                                "http://import-test.invalid/versioned/omn/1.0");
    }

    @Test
    public void detectsVersionIriInObo() throws IOException {
        // data-version gives the version IRI obo/<id>/<data-version>/<id>.owl, as the OWL API builds it.
        assertLocalCopyDetected("versioned.obo",
                                "http://purl.obolibrary.org/obo/t1vtoppings.owl",
                                "http://purl.obolibrary.org/obo/t1vtoppings.obo",
                                "http://purl.obolibrary.org/obo/t1vtoppings/2026-08-01/t1vtoppings.owl");
    }

    /*
     * Compatibility with the older scan, which indexed every XML file by its
     * xml:base: that value must still be listed, even a relative one. A declared
     * IRI that cannot be made absolute must not be listed at all.
     */

    @Test
    public void keepsRelativeXmlBaseMappingOfLegacyAlgorithm() throws IOException {
        // The old algorithm indexed this file under the raw string "relative/base";
        // catalog lookups accept that key, so the mapping must survive.
        assertLocalCopyDetected("relative-xmlbase.owl", "relative/base");
    }

    @Test
    public void opaqueBaseYieldsOnlyAbsoluteEntries() throws IOException {
        // rdf:about="#onto" cannot be resolved against an opaque urn: base into an
        // absolute IRI, so it must not be recorded; the raw xml:base still is.
        XMLCatalog catalog = catalogFor("opaque-base.owl");
        assertEquals(new File(new File(TEST_ROOT, "opaque-base-owl"), "opaque-base.owl").toURI(),
                     CatalogUtilities.getRedirect(URI.create("urn:example:base"), catalog));
        GroupEntry group = (GroupEntry) catalog.getEntries().get(0);
        for (org.protege.xmlcatalog.entry.Entry entry : group.getEntries()) {
            String name = ((org.protege.xmlcatalog.entry.UriEntry) entry).getName();
            assertTrue("Unexpected non-absolute catalog entry: " + name, URI.create(name).isAbsolute());
        }
    }

    /*
     * Turtle: text inside strings or comments must never produce a catalog entry,
     * and a comment must not hide a real declaration or its version IRI.
     */

    @Test
    public void turtleTextInsideLongStringLiteralsIsNotAnOntology() throws IOException {
        XMLCatalog catalog = catalogFor("turtle-literal-lookalike.ttl");
        GroupEntry group = (GroupEntry) catalog.getEntries().get(0);
        assertTrue("Expected no entries for declarations that occur only inside string literals",
                   group.getEntries().isEmpty());
    }

    @Test
    public void turtleCommentsAreIgnoredAndDoNotHideTheVersionIri() throws IOException {
        XMLCatalog catalog = catalogFor("turtle-comments.ttl");
        assertLocalCopyDetected("turtle-comments.ttl",
                                "http://import-test.invalid/formats/turtle-comments",
                                "http://import-test.invalid/formats/turtle-comments/v1");
        GroupEntry group = (GroupEntry) catalog.getEntries().get(0);
        for (org.protege.xmlcatalog.entry.Entry entry : group.getEntries()) {
            String name = ((org.protege.xmlcatalog.entry.UriEntry) entry).getName();
            assertTrue("Commented-out declaration leaked into the catalog: " + name,
                       !name.startsWith("http://commented.test/"));
        }
    }

    @Test
    public void turtleDeclarationAfterALongStringClosingMidLineIsFound() throws IOException {
        assertLocalCopyDetected("turtle-literal-closes-midline.ttl",
                                "http://import-test.invalid/formats/turtle-midline");
    }

    /*
     * Older RDF/XML shapes, as Protege 3 wrote them: rdf:ID declarations, and
     * xml:base on the owl:Ontology element itself. The expected IRIs are the ones
     * the OWL API assigns when it loads each file.
     */

    @Test
    public void detectsRdfIdOntologyDeclaration() throws IOException {
        // rdf:ID="onto" under xml:base B means B#onto.
        assertLocalCopyDetected("rdfid-ontology.owl", "http://import-test.invalid/formats/rdfid#onto");
    }

    @Test
    public void detectsAbsoluteXmlBaseOnTheOntologyElement() throws IOException {
        assertLocalCopyDetected("nested-xmlbase-absolute.owl",
                                "http://import-test.invalid/formats/nested/onto");
    }

    @Test
    public void detectsRelativeXmlBaseOnTheOntologyElementResolvedAgainstTheRootBase() throws IOException {
        // xml:base="sub/" on the element resolves against the root's base first.
        assertLocalCopyDetected("nested-xmlbase-relative.owl",
                                "http://import-test.invalid/formats/root/sub/onto");
    }

    /*
     * Text layouts the OWL API itself does not write: a UTF-8 byte order mark at
     * the start of the file (common from Windows editors), and functional syntax
     * with the IRIs on the lines after "Ontology(".
     */

    @Test
    public void detectsFunctionalSyntaxWithUtf8ByteOrderMark() throws IOException {
        assertLocalCopyDetected("bom.ofn", "http://import-test.invalid/formats/bom-ofn");
    }

    @Test
    public void detectsFunctionalSyntaxWithIrisOnTheLinesAfterOntologyKeyword() throws IOException {
        assertLocalCopyDetected("split-lines.ofn",
                                "http://import-test.invalid/formats/split-ofn",
                                "http://import-test.invalid/formats/split-ofn/1.0");
    }

    @Test
    public void oboFileWithoutOntologyTagYieldsNoEntry() throws IOException {
        // No "ontology:" tag means no IRI can be derived: the file must be
        // skipped silently (the anonymous-ontology rule).
        File folder = new File(TEST_ROOT, "toppings-notag-obo");
        folder.mkdirs();
        Files.copy(new File(SOURCE_DIR, "toppings-notag.obo").toPath(),
                   new File(folder, "toppings-notag.obo").toPath(),
                   StandardCopyOption.REPLACE_EXISTING);
        OntologyCatalogManager catalogManager
                = new OntologyCatalogManager(Collections.singletonList(new FolderGroupManager()));
        XMLCatalog catalog = catalogManager.ensureCatalogExists(folder);
        GroupEntry group = (GroupEntry) catalog.getEntries().get(0);
        assertTrue("Expected no catalog entries for an OBO file without an ontology tag",
                   group.getEntries().isEmpty());
    }

    @Test
    public void detectsOwlXmlWithoutXmlBase() throws IOException {
        assertLocalCopyDetected("toppings.owx",
                                "http://import-test.invalid/formats/toppings-owx");
    }

    private XMLCatalog catalogFor(String fixtureName) throws IOException {
        File folder = new File(TEST_ROOT, fixtureName.replace('.', '-'));
        folder.mkdirs();
        Files.copy(new File(SOURCE_DIR, fixtureName).toPath(), new File(folder, fixtureName).toPath(),
                   StandardCopyOption.REPLACE_EXISTING);
        OntologyCatalogManager catalogManager
                = new OntologyCatalogManager(Collections.singletonList(new FolderGroupManager()));
        return catalogManager.ensureCatalogExists(folder);
    }

    private void assertLocalCopyDetected(String fixtureName, String... ontologyIris) throws IOException {
        XMLCatalog catalog = catalogFor(fixtureName);
        File localCopy = new File(new File(TEST_ROOT, fixtureName.replace('.', '-')), fixtureName);

        for (String ontologyIri : ontologyIris) {
            URI redirect = CatalogUtilities.getRedirect(URI.create(ontologyIri), catalog);
            assertNotNull("Local copy " + fixtureName + " was not auto-detected as " + ontologyIri, redirect);
            assertEquals(localCopy.toURI(), redirect);
        }
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
