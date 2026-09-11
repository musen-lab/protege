package org.protege.editor.owl.model.declaration;

import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;

import static org.junit.Assert.*;

/**
 * Verifies the declaration check's runtime at scale.
 *
 * <p>The test checks 100,000 entities across 20 ontologies against an absolute time budget suitable
 * for running without a progress dialog.
 */
public class MissingDeclarationScale_TestCase {

    /** A deliberately generous upper bound for checking the 100,000-entity fixture. */
    private static final long BUDGET_MILLIS = 1000L;

    private static final int ONTOLOGIES = 20;

    private static final int ENTITIES = 100_000;

    private final MissingDeclarationChecker checker = new MissingDeclarationChecker();

    @Test
    public void shouldCheckOneHundredThousandEntitiesAcrossTwentyOntologiesWithinTheBudget() throws Exception {
        OWLOntology root = largeClosure();

        long start = System.nanoTime();
        MissingDeclarationReport report = checker.check(root);
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

        System.out.println("[scale] " + root.getImportsClosure().size() + " ontologies, "
                + ENTITIES + " terms checked in " + elapsedMillis + " ms");

        assertFalse(report.isEmpty());
        // Only the absolute budget is asserted. Comparing one measurement against another is
        // what makes a timing test flake on a busy machine.
        assertTrue("took " + elapsedMillis + " ms, budget is " + BUDGET_MILLIS + " ms",
                elapsedMillis < BUDGET_MILLIS);
    }

    /**
     * Creates a 20-ontology closure containing 100,000 entities, one tenth of them undeclared.
     *
     * @return the root ontology, which imports the other 19 ontologies
     * @throws Exception if an ontology in the fixture cannot be created
     */
    private static OWLOntology largeClosure() throws Exception {
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = m.getOWLDataFactory();
        IRI rootIri = IRI.create("http://example.org/scale/root");
        OWLOntology root = m.createOntology(rootIri);
        int perOntology = ENTITIES / ONTOLOGIES;
        for (int o = 1; o < ONTOLOGIES; o++) {
            IRI memberIri = IRI.create("http://example.org/scale/o" + o);
            OWLOntology member = m.createOntology(memberIri);
            m.applyChange(new AddImport(root, df.getOWLImportsDeclaration(memberIri)));
            addTerms(m, df, member, memberIri, perOntology);
        }
        addTerms(m, df, root, rootIri, perOntology);
        return root;
    }

    private static void addTerms(OWLOntologyManager m, OWLDataFactory df, OWLOntology o,
                                 IRI namespace, int count) {
        for (int i = 0; i < count; i++) {
            OWLClass term = df.getOWLClass(IRI.create(namespace + "#C" + i));
            if (i % 10 == 0) {
                // Left undeclared on purpose, so the check has real work to report.
                m.addAxiom(o, df.getOWLSubClassOfAxiom(term, df.getOWLThing()));
            } else {
                m.addAxiom(o, df.getOWLDeclarationAxiom(term));
            }
        }
    }
}
