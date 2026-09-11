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
 * Protects the default behavior of including automatic declarations when saving.
 *
 * <p>The test saves each ontology as RDF/XML, Turtle, OWL/XML, and Functional Syntax, then compares
 * its declarations with documents created before the setting was added.
 */
public class DeclarationBaseline_TestCase {

    @Test
    public void shouldHoldAtLeastFiveOntologies() {
        assertThat(DeclarationBaseline.TEST_ONTOLOGIES.size(), greaterThanOrEqualTo(5));
    }

    @Test
    public void shouldMatchExpectedDeclarationsForEveryTestOntology() throws Exception {
        SortedSet<String> mismatches = new TreeSet<>();
        for (String fileName : DeclarationBaseline.TEST_ONTOLOGIES) {
            OWLOntology ontology = DeclarationBaseline.loadTestOntology(fileName);
            for (Format format : Format.values()) {
                SortedSet<String> actual =
                        DeclarationBaseline.declarationsOf(
                                DeclarationBaseline.saveToString(ontology, format));
                SortedSet<String> expected = DeclarationBaseline.expectedDeclarations(fileName, format);
                if (!actual.equals(expected)) {
                    mismatches.add(describeMismatch(fileName, format, expected, actual));
                }
            }
        }
        assertEquals("Declaration output differs from the expected files:\n" + String.join("\n", mismatches),
                0, mismatches.size());
    }

    /** Ensures output does not depend on other ontologies loaded by the same Java process. */
    @Test
    public void shouldProduceTheSameOutputOnEverySave() throws Exception {
        for (String fileName : DeclarationBaseline.TEST_ONTOLOGIES) {
            for (Format format : Format.values()) {
                SortedSet<String> first = declarationsFor(fileName, format);
                DeclarationBaseline.loadTestOntology("pizza.owl");
                SortedSet<String> second = declarationsFor(fileName, format);
                assertThat(fileName + " / " + format + " changed between saves", second, is(first));
            }
        }
    }

    private SortedSet<String> declarationsFor(String fileName, Format format) throws Exception {
        return DeclarationBaseline.declarationsOf(
                DeclarationBaseline.saveToString(
                        DeclarationBaseline.loadTestOntology(fileName), format));
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
