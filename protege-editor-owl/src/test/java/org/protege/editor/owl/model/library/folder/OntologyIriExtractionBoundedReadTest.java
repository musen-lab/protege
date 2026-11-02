package org.protege.editor.owl.model.library.folder;

import org.junit.Before;
import org.junit.Test;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The folder scan reads at most a fixed number of bytes from any file, whatever
 * its size or line structure, so scanning a folder can never run Protege out of
 * memory. Eight megabytes covers every declaration seen in real files, including
 * Turtle whose statements are sorted so the ontology comes last.
 */
public class OntologyIriExtractionBoundedReadTest {

    private static final File TEST_ROOT = new File("target/bounded-read.test");
    private static final int MIB = 1024 * 1024;

    /** The scan's read limit, as set in OntologyIriExtractionAlgorithm. */
    private static final int LIMIT_MIB = 8;

    /** Well past the limit, so a declaration placed there must not be seen. */
    private static final int PAST_LIMIT_MIB = LIMIT_MIB + 1;

    /** A file many times the limit, to show memory follows the limit and not the file. */
    private static final int HUGE_FILE_MIB = 8 * LIMIT_MIB;

    private final OntologyIriExtractionAlgorithm algorithm = new OntologyIriExtractionAlgorithm();

    @Before
    public void cleanTestRoot() {
        File[] old = TEST_ROOT.listFiles();
        if (old != null) {
            for (File f : old) {
                f.delete();
            }
        }
        TEST_ROOT.mkdirs();
    }

    @Test
    public void hugeSingleLineFileIsRejectedWithBoundedMemory() throws IOException {
        File f = new File(TEST_ROOT, "one-line.owl");
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(f))) {
            byte[] chunk = new byte[MIB];
            Arrays.fill(chunk, (byte) 'x');
            for (int i = 0; i < HUGE_FILE_MIB; i++) {
                out.write(chunk);
            }
        }
        long before = usedHeap();
        Set<URI> result = algorithm.getSuggestions(f);
        long after = usedHeap();
        assertEquals(Collections.emptySet(), result);
        // A bounded read allocates on the order of the limit (held as one line
        // while it is decoded, so a few times the limit), never on the order of the
        // file. Unbounded, this grew by about 5x the file size.
        assertTrue("Scanning a " + HUGE_FILE_MIB + " MiB one-line file must not allocate on the order of the file size, grew by "
                           + (after - before) + " bytes", after - before < 6 * LIMIT_MIB * MIB);
    }

    @Test
    public void xmlDeclarationBeforeTheByteCapIsKeptWhenTheFileIsOversized() throws IOException {
        File f = new File(TEST_ROOT, "declared-early.owl");
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(f))) {
            out.write(("<?xml version=\"1.0\"?>\n<rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\""
                    + " xmlns:owl=\"http://www.w3.org/2002/07/owl#\">\n"
                    + "<owl:Ontology rdf:about=\"http://t.invalid/early\"/>\n<!-- ").getBytes(StandardCharsets.UTF_8));
            writePadding(out, PAST_LIMIT_MIB * MIB);
            out.write(" -->\n</rdf:RDF>\n".getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(Collections.singleton(URI.create("http://t.invalid/early")), algorithm.getSuggestions(f));
    }

    @Test
    public void xmlDeclarationAfterTheByteCapIsNotSeen() throws IOException {
        File f = new File(TEST_ROOT, "declared-late.owl");
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(f))) {
            out.write(("<?xml version=\"1.0\"?>\n<rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\""
                    + " xmlns:owl=\"http://www.w3.org/2002/07/owl#\">\n<!-- ").getBytes(StandardCharsets.UTF_8));
            writePadding(out, PAST_LIMIT_MIB * MIB);
            out.write((" -->\n<owl:Ontology rdf:about=\"http://t.invalid/late\"/>\n</rdf:RDF>\n")
                              .getBytes(StandardCharsets.UTF_8));
        }
        assertEquals(Collections.emptySet(), algorithm.getSuggestions(f));
    }

    private static long usedHeap() {
        System.gc();
        return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
    }

    private static void writePadding(OutputStream out, int bytes) throws IOException {
        byte[] chunk = new byte[64 * 1024];
        Arrays.fill(chunk, (byte) 'p');
        for (int written = 0; written < bytes; written += chunk.length) {
            out.write(chunk);
        }
    }
}
