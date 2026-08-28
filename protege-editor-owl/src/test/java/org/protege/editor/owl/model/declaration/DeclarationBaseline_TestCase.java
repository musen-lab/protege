package org.protege.editor.owl.model.declaration;

import org.junit.Test;
import org.protege.editor.owl.model.declaration.DeclarationBaseline.Format;
import org.semanticweb.owlapi.model.OWLOntology;

import java.util.SortedSet;
import java.util.TreeSet;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;

/**
 * The default output must not change.  Saves every corpus ontology to RDF/XML and Turtle with
 * declaration synthesis untouched, and compares the declarations against the baseline captured from
 * {@code master} before the setting existed.
 *
 * <p>See {@code src/test/resources/declarations/README.md} before regenerating anything.
 */
public class DeclarationBaseline_TestCase {

    @Test
    public void shouldHoldAtLeastFiveOntologies() {
        assertThat(DeclarationBaseline.CORPUS.size(), greaterThanOrEqualTo(5));
    }

    @Test
    public void shouldMatchBaselineDeclarationsForEveryCorpusOntology() throws Exception {
        SortedSet<String> mismatches = new TreeSet<>();
        for (String fileName : DeclarationBaseline.CORPUS) {
            OWLOntology ontology = DeclarationBaseline.loadCorpusOntology(fileName);
            for (Format format : Format.values()) {
                SortedSet<String> actual =
                        DeclarationBaseline.declarationsOf(DeclarationBaseline.render(ontology, format));
                SortedSet<String> expected = DeclarationBaseline.baselineDeclarations(fileName, format);
                if (!actual.equals(expected)) {
                    mismatches.add(describeMismatch(fileName, format, expected, actual));
                }
            }
        }
        assertEquals("Declaration output drifted from the captured baseline:\n" + String.join("\n", mismatches),
                0, mismatches.size());
    }

    /**
     * Renders twice in one JVM with an unrelated parse in between.  A corpus member whose output
     * depends on what the run parsed earlier would make the baseline flaky rather than useful.
     */
    @Test
    public void shouldRenderEveryCorpusOntologyDeterministically() throws Exception {
        for (String fileName : DeclarationBaseline.CORPUS) {
            for (Format format : Format.values()) {
                SortedSet<String> first = declarationsFor(fileName, format);
                DeclarationBaseline.loadCorpusOntology("pizza.owl");
                SortedSet<String> second = declarationsFor(fileName, format);
                assertThat(fileName + " / " + format + " is not deterministic", second, is(first));
            }
        }
    }

    private SortedSet<String> declarationsFor(String fileName, Format format) throws Exception {
        return DeclarationBaseline.declarationsOf(
                DeclarationBaseline.render(DeclarationBaseline.loadCorpusOntology(fileName), format));
    }

    private String describeMismatch(String fileName,
                                    Format format,
                                    SortedSet<String> expected,
                                    SortedSet<String> actual) {
        SortedSet<String> missing = new TreeSet<>(expected);
        missing.removeAll(actual);
        SortedSet<String> added = new TreeSet<>(actual);
        added.removeAll(expected);
        return "  " + fileName + " / " + format
                + "\n    no longer declared: " + missing
                + "\n    newly declared:     " + added;
    }
}
