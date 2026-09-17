package org.protege.editor.owl.model.declaration;

import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;

import javax.annotation.Nullable;

import static org.junit.Assert.assertEquals;

/**
 * Tests both declaration checks on a large ontology.
 */
public class DeclarationCheckerScale_TestCase {

    /** Maximum time allowed to check the large test ontology. */
    private static final long BUDGET_MILLIS = 1000L;

    private static final int ONTOLOGIES = 20;

    private static final int ENTITIES = 100_000;

    /** One in ten terms is used without a declaration. */
    private static final int EXPECTED_MISSING = ENTITIES / 10;

    /**
     * One in ten terms is declared by a neighbour rather than by its owner, except in the first
     * ontology, which has no neighbour to displace them into.
     */
    private static final int EXPECTED_MISPLACED = EXPECTED_MISSING - (ENTITIES / ONTOLOGIES / 10);

    private final DeclarationChecker checker = new DeclarationChecker();

    @Test
    public void shouldRunBothChecksOverOneHundredThousandEntities() throws Exception {
        OWLOntology root = largeClosure();

        long start = System.nanoTime();
        DeclarationReport report = checker.check(root);
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

        System.out.println("[scale] " + root.getImportsClosure().size() + " ontologies, "
                + ENTITIES + " terms, " + report.getMissing().size() + " missing and "
                + report.getMisplaced().size() + " misplaced in " + elapsedMillis
                + " ms (informational)");

        // One finding per defect, so nothing was missed and nothing was double-counted.
        assertEquals(EXPECTED_MISSING, report.getMissing().size());
        assertEquals(EXPECTED_MISPLACED, report.getMisplaced().size());

        // Every reported entity is one the fixture really left undeclared, so none of the declared
        // terms slipped into the missing report.
        for (MissingDeclarationFinding finding : report.getMissing().getFindings()) {
            assertEquals(0, termNumberOf(finding.getEntity()) % 10);
        }
        // And every misplaced finding is one the fixture really declared in a neighbour.
        for (MisplacedDeclarationFinding finding : report.getMisplaced().getFindings()) {
            assertEquals(1, termNumberOf(finding.getEntity()) % 10);
            assertEquals(DeclarationSeverity.WARNING, finding.getSeverity());
        }
    }

    private static int termNumberOf(OWLEntity entity) {
        String iri = entity.getIRI().toString();
        return Integer.parseInt(iri.substring(iri.lastIndexOf("#C") + 2));
    }

    /**
     * Creates 20 linked ontologies with missing and misplaced declarations.
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
        OWLOntology previous = null;
        for (int o = 1; o < ONTOLOGIES; o++) {
            IRI memberIri = IRI.create("http://example.org/scale/o" + o);
            OWLOntology member = m.createOntology(memberIri);
            m.applyChange(new AddImport(root, df.getOWLImportsDeclaration(memberIri)));
            addTerms(m, df, member, memberIri, perOntology, previous);
            previous = member;
        }
        addTerms(m, df, root, rootIri, perOntology, previous);
        return root;
    }

    private static void addTerms(OWLOntologyManager m, OWLDataFactory df, OWLOntology o,
                                 IRI namespace, int count, @Nullable OWLOntology neighbour) {
        for (int i = 0; i < count; i++) {
            OWLClass term = df.getOWLClass(IRI.create(namespace + "#C" + i));
            if (i % 10 == 0) {
                // Left undeclared on purpose, so the missing check has real work to report.
                m.addAxiom(o, df.getOWLSubClassOfAxiom(term, df.getOWLThing()));
            } else if (i % 10 == 1 && neighbour != null) {
                // Declared by a neighbour rather than its owner, which is a misplaced declaration.
                m.addAxiom(neighbour, df.getOWLDeclarationAxiom(term));
            } else {
                m.addAxiom(o, df.getOWLDeclarationAxiom(term));
            }
        }
    }
}
