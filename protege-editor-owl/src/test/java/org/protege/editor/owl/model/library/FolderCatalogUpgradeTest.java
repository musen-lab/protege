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
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Comparator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * A catalog written by an older Protege must gain the new mappings the first time
 * it is updated, even for files that have not changed since. Catalog updates keep
 * entries whose file is older than the entry, and skip the extraction algorithms
 * for such files, so the only way a legacy catalog learns about declared and
 * version IRIs is a version bump that forces one full regeneration.
 */
public class FolderCatalogUpgradeTest {

    private static final File SOURCE_DIR = new File("src/test/resources/ontologies/formats");
    private static final File TEST_ROOT = new File("target/catalog-upgrade.test");

    private static final String ONTOLOGY_IRI = "http://import-test.invalid/versioned/rdfxml";
    private static final String VERSION_IRI = "http://import-test.invalid/versioned/rdfxml/1.0";

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
    public void legacyVersion2CatalogGainsVersionIriForUnchangedFile() throws IOException {
        File folder = new File(TEST_ROOT, "legacy");
        folder.mkdirs();
        File localCopy = new File(folder, "versioned-rdfxml.owl");
        Files.copy(new File(SOURCE_DIR, "versioned-rdfxml.owl").toPath(), localCopy.toPath(),
                   StandardCopyOption.REPLACE_EXISTING);

        // A catalog as the xml:base-only algorithm of Protege 5.6.x wrote it: group
        // version 2, one entry for the ontology IRI, timestamp far enough in the
        // future that the file counts as unchanged and the entry is retained.
        long farFuture = System.currentTimeMillis() + 24L * 60 * 60 * 1000;
        String legacyCatalog = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n"
                + "<catalog prefer=\"public\" xmlns=\"urn:oasis:names:tc:entity:xmlns:xml:catalog\">\n"
                + "    <group id=\"Folder Repository, directory=, recursive=true, Auto-Update=true, version=2\" prefer=\"public\">\n"
                + "        <uri id=\"Automatically generated entry, Timestamp=" + farFuture + "\""
                + " name=\"" + ONTOLOGY_IRI + "\" uri=\"versioned-rdfxml.owl\"/>\n"
                + "    </group>\n"
                + "</catalog>\n";
        Files.write(new File(folder, "catalog-v001.xml").toPath(),
                    legacyCatalog.getBytes(StandardCharsets.UTF_8));

        OntologyCatalogManager catalogManager
                = new OntologyCatalogManager(Collections.singletonList(new FolderGroupManager()));
        XMLCatalog catalog = catalogManager.ensureCatalogExists(folder);

        URI ontologyRedirect = CatalogUtilities.getRedirect(URI.create(ONTOLOGY_IRI), catalog);
        assertNotNull("Legacy mapping must survive the upgrade", ontologyRedirect);
        assertEquals(localCopy.toURI(), ontologyRedirect);

        URI versionRedirect = CatalogUtilities.getRedirect(URI.create(VERSION_IRI), catalog);
        assertNotNull("Upgraded catalog must gain the version IRI for an unchanged file", versionRedirect);
        assertEquals(localCopy.toURI(), versionRedirect);
    }

    @Test
    public void legacyCatalogMigrationKeepsRootLevelUserEntries() throws IOException {
        File folder = new File(TEST_ROOT, "legacy-with-user-entry");
        folder.mkdirs();
        File localCopy = new File(folder, "versioned-rdfxml.owl");
        Files.copy(new File(SOURCE_DIR, "versioned-rdfxml.owl").toPath(), localCopy.toPath(),
                   StandardCopyOption.REPLACE_EXISTING);
        File userMapped = new File(folder, "elsewhere.owl");
        Files.copy(new File(SOURCE_DIR, "toppings-rdfxml-xmlbase.owl").toPath(), userMapped.toPath(),
                   StandardCopyOption.REPLACE_EXISTING);

        // The "Resolve missing import?" dialog writes its mapping as a root-level
        // catalog entry, outside the auto-generated group. Regenerating the group
        // for the version bump must not touch it.
        String userIri = "http://user.invalid/manually-mapped";
        long farFuture = System.currentTimeMillis() + 24L * 60 * 60 * 1000;
        String legacyCatalog = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n"
                + "<catalog prefer=\"public\" xmlns=\"urn:oasis:names:tc:entity:xmlns:xml:catalog\">\n"
                + "    <uri id=\"User Entered Import Resolution\" name=\"" + userIri + "\" uri=\"elsewhere.owl\"/>\n"
                + "    <group id=\"Folder Repository, directory=, recursive=true, Auto-Update=true, version=2\" prefer=\"public\">\n"
                + "        <uri id=\"Automatically generated entry, Timestamp=" + farFuture + "\""
                + " name=\"" + ONTOLOGY_IRI + "\" uri=\"versioned-rdfxml.owl\"/>\n"
                + "    </group>\n"
                + "</catalog>\n";
        Files.write(new File(folder, "catalog-v001.xml").toPath(),
                    legacyCatalog.getBytes(StandardCharsets.UTF_8));

        OntologyCatalogManager catalogManager
                = new OntologyCatalogManager(Collections.singletonList(new FolderGroupManager()));
        XMLCatalog catalog = catalogManager.ensureCatalogExists(folder);

        assertEquals("User entry must survive the migration",
                     userMapped.toURI(), CatalogUtilities.getRedirect(URI.create(userIri), catalog));
        assertEquals("Generated group must be rebuilt with the version IRI",
                     localCopy.toURI(), CatalogUtilities.getRedirect(URI.create(VERSION_IRI), catalog));
    }

    @Test
    public void migrationKeepsTheRelativeDirectoryOfAPortableCatalog() throws IOException {
        File folder = new File(TEST_ROOT, "portable");
        folder.mkdirs();
        Files.copy(new File(SOURCE_DIR, "versioned-rdfxml.owl").toPath(),
                   new File(folder, "versioned-rdfxml.owl").toPath(), StandardCopyOption.REPLACE_EXISTING);
        writeLegacyCatalog(folder, "");

        newManager().ensureCatalogExists(folder);

        String saved = new String(Files.readAllBytes(new File(folder, "catalog-v001.xml").toPath()), StandardCharsets.UTF_8);
        assertTrue("The migrated group must keep 'directory=' (relative to the catalog), got:\n" + saved,
                   saved.contains("directory=, recursive=true, Auto-Update=true, version=3"));
    }

    @Test
    public void migratedCatalogStillUpdatesAfterItsFolderMoves() throws IOException {
        File original = new File(TEST_ROOT, "movable");
        original.mkdirs();
        Files.copy(new File(SOURCE_DIR, "versioned-rdfxml.owl").toPath(),
                   new File(original, "versioned-rdfxml.owl").toPath(), StandardCopyOption.REPLACE_EXISTING);
        writeLegacyCatalog(original, "");
        newManager().ensureCatalogExists(original);

        File moved = new File(TEST_ROOT, "moved");
        Files.move(original.toPath(), moved.toPath());
        File added = new File(moved, "toppings.owl");
        Files.copy(new File(SOURCE_DIR, "toppings-rdfxml-xmlbase.owl").toPath(), added.toPath());
        XMLCatalog catalog = newManager().ensureCatalogExists(moved);

        assertEquals("A file added after the move must be scanned in the new location",
                     added.toURI(),
                     CatalogUtilities.getRedirect(URI.create("http://import-test.invalid/formats/toppings-rdfxml-xmlbase"), catalog));
    }

    @Test
    public void emptyLegacyCatalogIsSavedAtTheNewVersion() throws IOException {
        File folder = new File(TEST_ROOT, "empty-legacy");
        folder.mkdirs();
        writeLegacyCatalog(folder, "");

        newManager().ensureCatalogExists(folder);

        String saved = new String(Files.readAllBytes(new File(folder, "catalog-v001.xml").toPath()), StandardCharsets.UTF_8);
        assertTrue("An empty folder's catalog must still be written at version 3, got:\n" + saved,
                   saved.contains("version=3"));
    }

    @Test
    public void legacyCatalogWithOnlyAStaleEntryIsRewrittenWithoutIt() throws IOException {
        File folder = new File(TEST_ROOT, "stale-legacy");
        folder.mkdirs();
        long farFuture = System.currentTimeMillis() + 24L * 60 * 60 * 1000;
        writeLegacyCatalog(folder, "        <uri id=\"Automatically generated entry, Timestamp=" + farFuture + "\""
                + " name=\"http://import-test.invalid/gone\" uri=\"missing.owl\"/>\n");

        newManager().ensureCatalogExists(folder);

        String saved = new String(Files.readAllBytes(new File(folder, "catalog-v001.xml").toPath()), StandardCharsets.UTF_8);
        assertTrue("Catalog must be saved at version 3, got:\n" + saved, saved.contains("version=3"));
        assertFalse("The entry for a file that no longer exists must be gone, got:\n" + saved,
                    saved.contains("missing.owl"));
    }

    private static void writeLegacyCatalog(File folder, String groupChildren) throws IOException {
        String legacyCatalog = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n"
                + "<catalog prefer=\"public\" xmlns=\"urn:oasis:names:tc:entity:xmlns:xml:catalog\">\n"
                + "    <group id=\"Folder Repository, directory=, recursive=true, Auto-Update=true, version=2\" prefer=\"public\">\n"
                + groupChildren
                + "    </group>\n"
                + "</catalog>\n";
        Files.write(new File(folder, "catalog-v001.xml").toPath(), legacyCatalog.getBytes(StandardCharsets.UTF_8));
    }

    private static OntologyCatalogManager newManager() {
        return new OntologyCatalogManager(Collections.singletonList(new FolderGroupManager()));
    }
}
