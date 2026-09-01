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
 * The folder scan promises bounded reads (issue #11): a file is examined by
 * reading at most a fixed number of bytes, whatever its size or line structure,
 * so that scanning a folder can never exhaust the desktop process's heap
 * (review finding F2). One mebibyte is far beyond any real ontology declaration
 * offset (largest seen in the local corpus: about 2 KiB).
 */
public class OntologyIriExtractionBoundedReadTest {

    private static final File TEST_ROOT = new File("target/bounded-read.test");
    private static final int MIB = 1024 * 1024;

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
            for (int i = 0; i < 32; i++) {
                out.write(chunk);
            }
        }
        long before = usedHeap();
        Set<URI> result = algorithm.getSuggestions(f);
        long after = usedHeap();
        assertEquals(Collections.emptySet(), result);
        // A bounded read allocates on the order of the cap (1 MiB), not the file
        // (32 MiB). Unbounded, this grew by about 5x the file size.
        assertTrue("Scanning a 32 MiB one-line file must not allocate on the order of the file size, grew by "
                           + (after - before) + " bytes", after - before < 8 * MIB);
    }

    @Test
    public void xmlDeclarationBeforeTheByteCapIsKeptWhenTheFileIsOversized() throws IOException {
        File f = new File(TEST_ROOT, "declared-early.owl");
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(f))) {
            out.write(("<?xml version=\"1.0\"?>\n<rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\""
                    + " xmlns:owl=\"http://www.w3.org/2002/07/owl#\">\n"
                    + "<owl:Ontology rdf:about=\"http://t.invalid/early\"/>\n<!-- ").getBytes(StandardCharsets.UTF_8));
            writePadding(out, 2 * MIB);
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
            writePadding(out, 2 * MIB);
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
