package org.protege.editor.owl.model.declaration;

import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;

import java.util.Collections;

import static org.junit.Assert.*;

/**
 * Verifies the value and query semantics of declaration reports and findings.
 *
 * <p>A report preserves finding order, exposes stable severity-based subsets, and represents an
 * empty result with an empty report rather than an absent value. A finding exposes its entity,
 * entity type, severity, and referring ontologies, and findings with equal content compare equal.
 */
public class MissingDeclarationReport_TestCase {

    private static final OWLDataFactory DF = OWLManager.getOWLDataFactory();

    private MissingDeclarationFinding error;
    private MissingDeclarationFinding warningIndividual;
    private MissingDeclarationFinding warningThirdParty;
    private MissingDeclarationReport report;

    @Before
    public void setUp() throws Exception {
        OWLOntology ontology = OWLManager.createOWLOntologyManager()
                .createOntology(IRI.create("http://example.org/report"));

        error = MissingDeclarationFinding.get(
                DF.getOWLClass(IRI.create("http://example.org/mine#A")),
                Collections.singleton(ontology));
        warningIndividual = MissingDeclarationFinding.get(
                DF.getOWLNamedIndividual(IRI.create("http://example.org/mine#i")),
                Collections.singleton(ontology));
        warningThirdParty = MissingDeclarationFinding.get(
                DF.getOWLAnnotationProperty(IRI.create("http://purl.org/dc/terms/title")),
                Collections.singleton(ontology));

        report = MissingDeclarationReport.get(
                ImmutableList.of(error, warningIndividual, warningThirdParty));
    }

    @Test
    public void shouldPreserveFindingInsertionOrder() {
        assertEquals(ImmutableList.of(error, warningIndividual, warningThirdParty),
                report.getFindings());
        assertEquals(3, report.size());
        assertFalse(report.isEmpty());
    }

    @Test
    public void shouldFilterFindingsBySeverity() {
        assertEquals(ImmutableList.of(error), report.getFindings(DeclarationSeverity.ERROR));
        assertEquals(ImmutableList.of(warningIndividual, warningThirdParty),
                report.getFindings(DeclarationSeverity.WARNING));
    }

    @Test
    public void shouldReturnTheSameResultsAcrossRepeatedReads() {
        // The report never re-runs the check, so repeated reads are free and always agree.
        assertEquals(report.getFindings(DeclarationSeverity.ERROR),
                report.getFindings(DeclarationSeverity.ERROR));
        assertEquals(report.getFindings(), report.getFindings());
    }

    @Test
    public void shouldRepresentAnEmptyResultWithoutAnAbsentReport() {
        MissingDeclarationReport empty = MissingDeclarationReport.get(Collections.emptyList());

        assertNotNull(empty);
        assertTrue(empty.isEmpty());
        assertEquals(0, empty.size());
        assertTrue(empty.getFindings().isEmpty());
        assertTrue(empty.getFindings(DeclarationSeverity.ERROR).isEmpty());
    }

    @Test
    public void shouldExposeEntityTypeSeverityAndReferringOntologiesOnAFinding() throws Exception {
        OWLOntology ontology = OWLManager.createOWLOntologyManager()
                .createOntology(IRI.create("http://example.org/one"));
        OWLClass term = DF.getOWLClass(IRI.create("http://example.org/one#A"));

        MissingDeclarationFinding finding =
                MissingDeclarationFinding.get(term, Collections.singleton(ontology));

        assertEquals(term, finding.getEntity());
        assertEquals(EntityType.CLASS, finding.getEntityType());
        assertEquals(DeclarationSeverity.ERROR, finding.getSeverity());
        assertEquals(Collections.singleton(ontology), finding.getReferringOntologies());
    }

    @Test
    public void shouldConsiderFindingsWithTheSameContentEqual() throws Exception {
        OWLOntology ontology = OWLManager.createOWLOntologyManager()
                .createOntology(IRI.create("http://example.org/two"));
        OWLClass term = DF.getOWLClass(IRI.create("http://example.org/two#A"));

        assertEquals(MissingDeclarationFinding.get(term, Collections.singleton(ontology)),
                MissingDeclarationFinding.get(term, Collections.singleton(ontology)));
    }
}
