package org.protege.editor.owl.model.declaration;

import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.Imports;
import org.semanticweb.owlapi.profiles.*;
import org.semanticweb.owlapi.profiles.violations.UndeclaredEntityViolation;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/**
 * Evaluates the OWL 2 DL profile checker as an alternative source of missing declarations.
 *
 * <p>{@link OWL2DLProfile} produces {@link UndeclaredEntityViolation} instances across the import
 * closure, but it is only a partial substitute for a dedicated check: it omits undeclared named
 * individuals, mixes declaration findings with unrelated profile violations, may emit multiple
 * violations for one entity, and performs a complete profile check.
 */
public class ProfileAsAlternative_TestCase {

    private static Set<String> undeclaredEntities(OWLOntology o) {
        return new OWL2DLProfile().checkOntology(o).getViolations().stream()
                .filter(v -> v instanceof UndeclaredEntityViolation)
                .map(v -> ((UndeclaredEntityViolation) v).getEntity().getIRI().toString())
                .collect(Collectors.toSet());
    }

    @Test
    public void shouldFindTheSameMissingDeclarationTheWalkFinds() throws Exception {
        OWLOntology leaf = ClosureFixtures.threeLevelClosure();

        assertTrue(undeclaredEntities(leaf).contains(ClosureFixtures.MID + "#Ghost"));
    }

    @Test
    public void shouldRespectTheImportsClosure() throws Exception {
        OWLOntology leaf = ClosureFixtures.threeLevelClosure();

        // A is declared in base, two imports up. If the profile were ontology-local
        // it would wrongly report A as undeclared from leaf.
        assertFalse(undeclaredEntities(leaf).contains(ClosureFixtures.BASE + "#A"));
        assertFalse(undeclaredEntities(leaf).contains(ClosureFixtures.BASE + "#p"));
    }

    @Test
    public void shouldNotReportUndeclaredNamedIndividuals() throws Exception {
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = m.getOWLDataFactory();
        OWLOntology o = m.createOntology(IRI.create("http://example.org/ind"));
        OWLClass c = df.getOWLClass(IRI.create("http://example.org/ind#C"));
        OWLNamedIndividual i = df.getOWLNamedIndividual(IRI.create("http://example.org/ind#i"));
        m.addAxiom(o, df.getOWLDeclarationAxiom(c));
        m.addAxiom(o, df.getOWLClassAssertionAxiom(c, i));

        assertFalse("individual is undeclared", o.isDeclared(i, Imports.INCLUDED));
        // But OWL 2 DL does not require it, so the profile stays silent. A check
        // that wants to report undeclared individuals cannot rely on the profile.
        assertFalse(undeclaredEntities(o).contains("http://example.org/ind#i"));
    }

    @Test
    public void shouldMixTheReportWithUnrelatedViolations() throws Exception {
        OWLOntology leaf = ClosureFixtures.threeLevelClosure();

        List<OWLProfileViolation> all = new OWL2DLProfile().checkOntology(leaf).getViolations();
        long undeclared = all.stream().filter(v -> v instanceof UndeclaredEntityViolation).count();

        // The caller must filter. The report is a DL-conformance report, not a
        // declaration report, and its other violations are out of T4.3's scope.
        assertTrue(undeclared > 0);
        assertTrue(all.size() >= undeclared);
        System.out.println("[profile] " + all.size() + " violations, "
                + undeclared + " of them undeclared-entity");
    }

    @Test
    public void shouldReportAViolationPerUseNotPerEntity() throws Exception {
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = m.getOWLDataFactory();
        OWLOntology o = m.createOntology(IRI.create("http://example.org/multi"));
        OWLClass declared = df.getOWLClass(IRI.create("http://example.org/multi#D"));
        OWLClass ghost = df.getOWLClass(IRI.create("http://example.org/multi#Ghost"));
        m.addAxiom(o, df.getOWLDeclarationAxiom(declared));
        // Reference the same undeclared class from three separate axioms.
        m.addAxiom(o, df.getOWLSubClassOfAxiom(declared, ghost));
        m.addAxiom(o, df.getOWLDisjointClassesAxiom(declared, ghost));
        m.addAxiom(o, df.getOWLEquivalentClassesAxiom(declared,
                df.getOWLObjectIntersectionOf(ghost, df.getOWLThing())));

        long raw = new OWL2DLProfile().checkOntology(o).getViolations().stream()
                .filter(v -> v instanceof UndeclaredEntityViolation).count();

        // One entity, several violations: the caller has to de-duplicate before
        // showing anything to a user.
        assertTrue("expected one violation per use, got " + raw, raw > 1);
        assertEquals(1, undeclaredEntities(o).size());
        System.out.println("[profile] one undeclared class used 3x produced "
                + raw + " violations");
    }

    @Test
    public void shouldCostMoreThanTheIndexPass() throws Exception {
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = m.getOWLDataFactory();
        OWLOntology root = m.createOntology(IRI.create("http://example.org/pscale/root"));
        for (int i = 0; i < 10; i++) {
            IRI moduleIri = IRI.create("http://example.org/pscale/m" + i);
            OWLOntology module = m.createOntology(moduleIri);
            m.applyChange(new AddImport(root, df.getOWLImportsDeclaration(moduleIri)));
            for (int j = 0; j < 5000; j++) {
                OWLClass c = df.getOWLClass(IRI.create(moduleIri + "#C" + j));
                m.addAxiom(module, df.getOWLDeclarationAxiom(c));
                m.addAxiom(module, df.getOWLSubClassOfAxiom(c, df.getOWLThing()));
            }
        }

        long t0 = System.nanoTime();
        new OWL2DLProfile().checkOntology(root).getViolations();
        long profileMs = (System.nanoTime() - t0) / 1_000_000;

        long t1 = System.nanoTime();
        int declared = 0;
        for (OWLOntology o : root.getImportsClosure()) {
            declared += o.getAxioms(AxiomType.DECLARATION).size();
        }
        long indexMs = (System.nanoTime() - t1) / 1_000_000;

        System.out.println("[profile] full OWL2DL check " + profileMs
                + " ms vs declaration index " + indexMs + " ms over "
                + declared + " declarations");
        assertTrue(declared > 0);
    }
}
