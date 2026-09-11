package org.protege.editor.owl.model.declaration;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;

/**
 * Provides the in-memory import closures shared by the declaration tests.
 *
 * <p>Every ontology in a fixture belongs to the same manager, allowing
 * {@link OWLOntology#getImportsClosure()} to resolve all import declarations without file or
 * network access.
 */
final class ClosureFixtures {

    static final String BASE = "http://example.org/base";
    static final String MID  = "http://example.org/mid";
    static final String LEAF = "http://example.org/leaf";

    private ClosureFixtures() {}

    static IRI iri(String s) { return IRI.create(s); }

    /**
     * Creates a three-level import closure: {@code leaf -> mid -> base}.
     *
     * <ul>
     *   <li>{@code base} declares class {@code A} and object property {@code p};</li>
     *   <li>{@code mid} declares {@code B}, makes it a subclass of {@code A}, and references the
     *       undeclared class {@code Ghost};</li>
     *   <li>{@code leaf} declares {@code C} and {@code base#Misplaced}, placing the latter outside
     *       the ontology identified by its namespace.</li>
     * </ul>
     *
     * @return the leaf ontology, whose manager also contains the middle and base ontologies
     * @throws OWLOntologyCreationException if an ontology in the fixture cannot be created
     */
    static OWLOntology threeLevelClosure() throws OWLOntologyCreationException {
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = m.getOWLDataFactory();

        OWLOntology base = m.createOntology(iri(BASE));
        OWLClass a = df.getOWLClass(iri(BASE + "#A"));
        OWLObjectProperty p = df.getOWLObjectProperty(iri(BASE + "#p"));
        m.addAxiom(base, df.getOWLDeclarationAxiom(a));
        m.addAxiom(base, df.getOWLDeclarationAxiom(p));

        OWLOntology mid = m.createOntology(iri(MID));
        m.applyChange(new AddImport(mid, df.getOWLImportsDeclaration(iri(BASE))));
        OWLClass b = df.getOWLClass(iri(MID + "#B"));
        OWLClass ghost = df.getOWLClass(iri(MID + "#Ghost"));
        m.addAxiom(mid, df.getOWLDeclarationAxiom(b));
        m.addAxiom(mid, df.getOWLSubClassOfAxiom(b, a));
        // Ghost is referenced by an axiom but never declared anywhere.
        m.addAxiom(mid, df.getOWLSubClassOfAxiom(b, ghost));

        OWLOntology leaf = m.createOntology(iri(LEAF));
        m.applyChange(new AddImport(leaf, df.getOWLImportsDeclaration(iri(MID))));
        OWLClass c = df.getOWLClass(iri(LEAF + "#C"));
        OWLClass misplaced = df.getOWLClass(iri(BASE + "#Misplaced"));
        m.addAxiom(leaf, df.getOWLDeclarationAxiom(c));
        // Declaration sits in leaf; the entity's namespace belongs to base.
        m.addAxiom(leaf, df.getOWLDeclarationAxiom(misplaced));
        m.addAxiom(leaf, df.getOWLSubClassOfAxiom(c, misplaced));

        return leaf;
    }
}
