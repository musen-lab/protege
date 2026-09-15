package org.protege.editor.owl.model.declaration;

import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.Imports;

import static org.junit.Assert.*;

/**
 * Checks declaration results across a large import closure.
 *
 * <p>The fixture contains 100,000 classes in 20 ontologies. Every tenth class is undeclared, so the
 * expected result is 10,000 errors. Runtime is logged but not asserted.
 *
 * @see ClosureWalkScale_TestCase
 */
public class MissingDeclarationScale_TestCase {

    private static final int ONTOLOGIES = 20;

    private static final int ENTITIES = 100_000;

    /** Every nth class is left undeclared, which fixes the number of findings to expect. */
    private static final int UNDECLARED_EVERY = 10;

    private static final int EXPECTED_FINDINGS = ENTITIES / UNDECLARED_EVERY;

    private final MissingDeclarationChecker checker = new MissingDeclarationChecker();

    @Test
    public void shouldReportEveryUndeclaredEntityInALargeClosure() throws Exception {
        OWLOntology root = largeClosure();
        // owl:Thing is in the signature because the undeclared classes are used as its subclasses.
        assertEquals(ENTITIES + 1, root.getSignature(Imports.INCLUDED).size());
        assertEquals(ONTOLOGIES, root.getImportsClosure().size());

        long start = System.nanoTime();
        MissingDeclarationReport report = checker.check(root);
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

        System.out.println("[scale] " + ONTOLOGIES + " ontologies, " + ENTITIES
                + " terms checked in " + elapsedMillis + " ms (informational)");

        // One finding per undeclared class, so nothing was missed and nothing was double-counted.
        assertEquals(EXPECTED_FINDINGS, report.size());
        // Every one of them is a class, so every one is an error and none is a warning.
        assertEquals(EXPECTED_FINDINGS, report.getFindings(DeclarationSeverity.ERROR).size());
        assertTrue(report.getFindings(DeclarationSeverity.WARNING).isEmpty());

        // And every entity reported is one the fixture actually left undeclared, so none of the
        // 90,000 declared classes slipped into the report.
        for (MissingDeclarationFinding finding : report.getFindings()) {
            String iri = finding.getEntity().getIRI().toString();
            int index = Integer.parseInt(iri.substring(iri.lastIndexOf("#C") + 2));
            assertEquals(iri + " is declared by the fixture", 0, index % UNDECLARED_EVERY);
        }
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
            if (i % UNDECLARED_EVERY == 0) {
                // Left undeclared on purpose, so the check has real work to report.
                m.addAxiom(o, df.getOWLSubClassOfAxiom(term, df.getOWLThing()));
            } else {
                m.addAxiom(o, df.getOWLDeclarationAxiom(term));
            }
        }
    }
}
