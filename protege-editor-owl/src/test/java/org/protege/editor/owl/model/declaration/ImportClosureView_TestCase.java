package org.protege.editor.owl.model.declaration;

import org.junit.Before;
import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.AddImport;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests the {@link ImportClosureView} that {@link DeclarationIndex} builds from real ontologies.
 *
 * <p>The view is the boundary between an ownership rule and the OWL import closure. These tests
 * verify that the index presents the root and every imported ontology, attributes an entity only
 * to documents whose own axioms mention it, and counts a declaration as a mention.
 *
 * <p>They also define the reachability relation exposed to rules: imports are directed and
 * transitive, siblings do not reach each other, and an ontology reaches itself only through an
 * import cycle.
 */
public class ImportClosureView_TestCase {

    private OWLOntologyManager manager;

    private OWLDataFactory dataFactory;

    @Before
    public void setUp() {
        manager = OWLManager.createOWLOntologyManager();
        dataFactory = manager.getOWLDataFactory();
    }

    @Test
    public void shouldReachThroughAChainOfImports() throws Exception {
        OWLOntology base = ontology("http://example.org/base");
        OWLOntology mid = ontology("http://example.org/mid");
        OWLOntology leaf = ontology("http://example.org/leaf");
        importInto(mid, base);
        importInto(leaf, mid);
        ImportClosureView view = DeclarationIndex.over(leaf);

        assertTrue("a direct import is reached", view.reaches(id(leaf), id(mid)));
        assertTrue("an import of an import is reached", view.reaches(id(leaf), id(base)));
        assertFalse("reaching does not run upwards", view.reaches(id(base), id(leaf)));
    }

    @Test
    public void shouldNotReachBetweenSiblings() throws Exception {
        OWLOntology one = ontology("http://example.org/one");
        OWLOntology other = ontology("http://example.org/other");
        OWLOntology root = ontology("http://example.org/root");
        importInto(root, one);
        importInto(root, other);
        ImportClosureView view = DeclarationIndex.over(root);

        assertTrue(view.reaches(id(root), id(one)));
        assertTrue(view.reaches(id(root), id(other)));
        assertFalse("two documents imported side by side reach nothing of each other",
                view.reaches(id(one), id(other)));
        assertFalse(view.reaches(id(other), id(one)));
    }

    @Test
    public void shouldReachBothWaysAroundACycle() throws Exception {
        OWLOntology first = ontology("http://example.org/first");
        OWLOntology second = ontology("http://example.org/second");
        importInto(first, second);
        importInto(second, first);
        ImportClosureView view = DeclarationIndex.over(first);

        // Neither is earlier than the other, which is what keeps a cycle from having an owner.
        assertTrue(view.reaches(id(first), id(second)));
        assertTrue(view.reaches(id(second), id(first)));
        assertTrue("a cycle leads back to its own start", view.reaches(id(first), id(first)));
    }

    @Test
    public void shouldNotReachItselfWithoutACycle() throws Exception {
        OWLOntology alone = ontology("http://example.org/alone");
        ImportClosureView view = DeclarationIndex.over(alone);

        assertFalse(view.reaches(id(alone), id(alone)));
    }

    @Test
    public void shouldReportTheDocumentsMentioningAnEntity() throws Exception {
        OWLOntology base = ontology("http://example.org/base");
        OWLOntology leaf = ontology("http://example.org/leaf");
        importInto(leaf, base);
        OWLClass term = clazz("http://example.org/vocab#Term");
        OWLClass parent = clazz("http://example.org/vocab#Parent");
        manager.addAxiom(base, dataFactory.getOWLSubClassOfAxiom(term, parent));
        ImportClosureView view = DeclarationIndex.over(leaf);

        assertEquals("only the document whose own axioms use it mentions it",
                1, view.getMentioningOntologies(term).size());
        assertTrue(view.getMentioningOntologies(term).contains(id(base)));
    }

    @Test
    public void shouldCountADeclarationAsAMention() throws Exception {
        OWLOntology base = ontology("http://example.org/base");
        OWLOntology leaf = ontology("http://example.org/leaf");
        importInto(leaf, base);
        OWLClass term = clazz("http://example.org/vocab#Term");
        manager.addAxiom(base, dataFactory.getOWLSubClassOfAxiom(term, dataFactory.getOWLThing()));
        manager.addAxiom(leaf, dataFactory.getOWLDeclarationAxiom(term));
        ImportClosureView view = DeclarationIndex.over(leaf);

        assertEquals(2, view.getMentioningOntologies(term).size());
    }

    @Test
    public void shouldMentionNothingForAnEntityOutsideTheClosure() throws Exception {
        OWLOntology base = ontology("http://example.org/base");
        ImportClosureView view = DeclarationIndex.over(base);

        assertTrue(view.getMentioningOntologies(clazz("http://example.org/absent#Term")).isEmpty());
    }

    @Test
    public void shouldHoldEveryDocumentOfTheClosure() throws Exception {
        OWLOntology base = ontology("http://example.org/base");
        OWLOntology leaf = ontology("http://example.org/leaf");
        importInto(leaf, base);
        ImportClosureView view = DeclarationIndex.over(leaf);

        assertEquals(2, view.getOntologies().size());
        assertTrue(view.getOntologies().contains(id(base)));
        assertTrue(view.getOntologies().contains(id(leaf)));
    }

    private void importInto(OWLOntology importer, OWLOntology imported) {
        manager.applyChange(new AddImport(importer,
                dataFactory.getOWLImportsDeclaration(imported.getOntologyID().getOntologyIRI().get())));
    }

    private static org.semanticweb.owlapi.model.OWLOntologyID id(OWLOntology ontology) {
        return ontology.getOntologyID();
    }

    private OWLOntology ontology(String iri) throws Exception {
        return manager.createOntology(IRI.create(iri));
    }

    private OWLClass clazz(String iri) {
        return dataFactory.getOWLClass(IRI.create(iri));
    }
}
