package org.protege.editor.owl.model.declaration;

import org.junit.Test;
import org.protege.editor.owl.model.declaration.DeclarationBaseline.Format;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;

import java.util.SortedSet;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.assertTrue;

/**
 * Checks the corpus is intact and that {@link DeclarationBaseline} reports what a document really
 * carries, using an ontology small enough to write the expected answer out by hand.
 */
public class DeclarationBaselineSupport_TestCase {

    private static final String NS = "http://example.invalid/support#";

    @Test
    public void shouldLoadEveryCorpusOntologyWithAxioms() throws Exception {
        for (String fileName : DeclarationBaseline.CORPUS) {
            OWLOntology ontology = DeclarationBaseline.loadCorpusOntology(fileName);
            assertThat("axiom count for " + fileName, ontology.getAxiomCount(), greaterThan(0));
            assertThat("declaration count for " + fileName,
                    ontology.getAxiomCount(org.semanticweb.owlapi.model.AxiomType.DECLARATION),
                    greaterThan(0));
        }
    }

    @Test
    public void shouldReportTheTwoDeclarationsAHandBuiltOntologyCarries() throws Exception {
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = manager.getOWLDataFactory();
        OWLOntology ontology = manager.createOntology(IRI.create("http://example.invalid/support"));
        OWLClass cls = df.getOWLClass(IRI.create(NS + "Thing1"));
        OWLObjectProperty property = df.getOWLObjectProperty(IRI.create(NS + "relates"));
        manager.addAxiom(ontology, df.getOWLDeclarationAxiom(cls));
        manager.addAxiom(ontology, df.getOWLDeclarationAxiom(property));

        for (Format format : Format.values()) {
            SortedSet<String> declarations =
                    DeclarationBaseline.declarationsOf(DeclarationBaseline.render(ontology, format));
            assertThat(format.name(), declarations,
                    contains("Class " + NS + "Thing1", "ObjectProperty " + NS + "relates"));
        }
    }

    @Test
    public void shouldRenderBothFormatsAsNonEmptyDocuments() throws Exception {
        OWLOntology ontology = DeclarationBaseline.loadCorpusOntology("twoEq.owl");
        for (Format format : Format.values()) {
            String document = DeclarationBaseline.render(ontology, format);
            assertTrue(format.name() + " document should not be empty", document.trim().length() > 0);
        }
    }
}
