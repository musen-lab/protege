package org.protege.editor.owl.model.declaration;

import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.Imports;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/**
 * Verifies the findings produced by {@link MissingDeclarationChecker} and the entities it ignores.
 *
 * <p>The checker reports each non-standard entity that is referenced but undeclared throughout the
 * import closure exactly once. Findings are deterministic, identify every referring ontology, and
 * distinguish errors in the ontology's vocabulary from warnings for third-party vocabulary and
 * named individuals. The checker also handles punning and ontology annotations without modifying
 * any ontology in the closure.
 */
public class MissingDeclarationChecker_TestCase {

    private MissingDeclarationChecker checker;

    @Before
    public void setUp() {
        checker = new MissingDeclarationChecker();
    }

    @Test
    public void shouldReportAnEntityUndeclaredThroughoutTheClosure() throws Exception {
        OWLOntology leaf = ClosureFixtures.threeLevelClosure();

        MissingDeclarationReport report = checker.check(leaf);

        assertEquals(names(report), ImmutableList.of(ClosureFixtures.MID + "#Ghost"));
        assertEquals(DeclarationSeverity.ERROR, report.getFindings().get(0).getSeverity());
    }

    @Test
    public void shouldNotReportAnEntityDeclaredByAnImport() throws Exception {
        OWLOntology leaf = ClosureFixtures.threeLevelClosure();

        List<String> reported = names(checker.check(leaf));

        assertFalse(reported.contains(ClosureFixtures.BASE + "#A"));
        assertFalse(reported.contains(ClosureFixtures.BASE + "#p"));
    }

    @Test
    public void shouldNotReportALocallyDeclaredEntityAlsoDeclaredByAnImport() throws Exception {
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = m.getOWLDataFactory();
        IRI baseIri = IRI.create("http://example.org/localbase");
        IRI moduleIri = IRI.create("http://example.org/localmodule");
        OWLOntology base = m.createOntology(baseIri);
        OWLOntology module = m.createOntology(moduleIri);
        m.applyChange(new AddImport(module, df.getOWLImportsDeclaration(baseIri)));
        OWLClass shared = df.getOWLClass(IRI.create(baseIri + "#Shared"));
        m.addAxiom(base, df.getOWLDeclarationAxiom(shared));
        m.addAxiom(module, df.getOWLDeclarationAxiom(shared));

        assertTrue(checker.check(module).isEmpty());
    }

    @Test
    public void shouldReturnAnEmptyReportForACleanOntology() throws Exception {
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = m.getOWLDataFactory();
        OWLOntology o = m.createOntology(IRI.create("http://example.org/clean"));
        OWLClass a = df.getOWLClass(IRI.create("http://example.org/clean#A"));
        OWLClass b = df.getOWLClass(IRI.create("http://example.org/clean#B"));
        m.addAxiom(o, df.getOWLDeclarationAxiom(a));
        m.addAxiom(o, df.getOWLDeclarationAxiom(b));
        m.addAxiom(o, df.getOWLSubClassOfAxiom(a, b));

        MissingDeclarationReport report = checker.check(o);

        assertNotNull(report);
        assertTrue(report.isEmpty());
        assertTrue(report.getFindings().isEmpty());
    }

    @Test
    public void shouldReportAnEntityOnceWhenItIsUsedMultipleTimes() throws Exception {
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = m.getOWLDataFactory();
        IRI upperIri = IRI.create("http://example.org/upper");
        IRI lowerIri = IRI.create("http://example.org/lower");
        OWLOntology upper = m.createOntology(upperIri);
        OWLOntology lower = m.createOntology(lowerIri);
        m.applyChange(new AddImport(lower, df.getOWLImportsDeclaration(upperIri)));
        OWLClass missing = df.getOWLClass(IRI.create("http://example.org/shared#Missing"));
        for (int i = 0; i < 3; i++) {
            OWLClass user = df.getOWLClass(IRI.create(upperIri + "#User" + i));
            m.addAxiom(upper, df.getOWLDeclarationAxiom(user));
            m.addAxiom(upper, df.getOWLSubClassOfAxiom(user, missing));
        }
        OWLClass alsoUses = df.getOWLClass(IRI.create(lowerIri + "#AlsoUses"));
        m.addAxiom(lower, df.getOWLDeclarationAxiom(alsoUses));
        m.addAxiom(lower, df.getOWLSubClassOfAxiom(alsoUses, missing));

        MissingDeclarationReport report = checker.check(lower);

        assertEquals(ImmutableList.of("http://example.org/shared#Missing"), names(report));
        assertEquals("both ontologies use it, so both are named",
                2, report.getFindings().get(0).getReferringOntologies().size());
    }

    @Test
    public void shouldIncludeEntityTypeSeverityAndReferringOntologiesInAFinding() throws Exception {
        OWLOntology leaf = ClosureFixtures.threeLevelClosure();
        OWLDataFactory df = leaf.getOWLOntologyManager().getOWLDataFactory();
        OWLClass ghost = df.getOWLClass(IRI.create(ClosureFixtures.MID + "#Ghost"));

        MissingDeclarationFinding finding = checker.check(leaf).getFindings().get(0);

        assertEquals(ghost, finding.getEntity());
        assertEquals(EntityType.CLASS, finding.getEntityType());
        assertEquals(DeclarationSeverity.ERROR, finding.getSeverity());
        assertEquals(1, finding.getReferringOntologies().size());
        assertEquals(ClosureFixtures.MID, finding.getReferringOntologies().iterator().next()
                .getOntologyID().getOntologyIRI().get().toString());
    }

    @Test
    public void shouldReturnTheSameOrderedFindingsAcrossRuns() throws Exception {
        OWLOntology o = manyUndeclaredTerms();

        MissingDeclarationReport first = checker.check(o);
        MissingDeclarationReport second = checker.check(o);

        assertEquals(first.getFindings(), second.getFindings());
        assertEquals(names(first), names(second));
    }

    @Test
    public void shouldOrderFindingsByIri() throws Exception {
        OWLOntology o = manyUndeclaredTerms();

        List<String> reported = names(checker.check(o));

        List<String> sorted = new ArrayList<>(reported);
        sorted.sort(String::compareTo);
        assertEquals("the order is imposed by sorting, not taken from a hash set",
                sorted, reported);
        assertTrue(reported.size() > 10);
    }

    @Test
    public void shouldReportOnlyTheUndeclaredEntityForAPunnedIri() throws Exception {
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = m.getOWLDataFactory();
        OWLOntology o = m.createOntology(IRI.create("http://example.org/pun"));
        IRI shared = IRI.create("http://example.org/pun#Thing");
        OWLClass asClass = df.getOWLClass(shared);
        OWLNamedIndividual asIndividual = df.getOWLNamedIndividual(shared);
        m.addAxiom(o, df.getOWLDeclarationAxiom(asClass));
        m.addAxiom(o, df.getOWLClassAssertionAxiom(asClass, asIndividual));

        MissingDeclarationReport report = checker.check(o);

        assertEquals(1, report.size());
        MissingDeclarationFinding finding = report.getFindings().get(0);
        assertEquals(asIndividual, finding.getEntity());
        assertEquals(EntityType.NAMED_INDIVIDUAL, finding.getEntityType());
        assertEquals(DeclarationSeverity.WARNING, finding.getSeverity());
    }

    @Test
    public void shouldReportAnAnnotationPropertyUsedOnlyInAnOntologyAnnotation() throws Exception {
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = m.getOWLDataFactory();
        OWLOntology o = m.createOntology(IRI.create("http://example.org/ann"));
        OWLAnnotationProperty note =
                df.getOWLAnnotationProperty(IRI.create("http://example.org/ann#note"));
        m.applyChange(new AddOntologyAnnotation(o, df.getOWLAnnotation(note, df.getOWLLiteral("x"))));

        assertEquals(ImmutableList.of("http://example.org/ann#note"), names(checker.check(o)));
    }

    @Test
    public void shouldNotReportAnIriUsedOnlyAsAnAnnotationValue() throws Exception {
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = m.getOWLDataFactory();
        OWLOntology o = m.createOntology(IRI.create("http://example.org/annval"));
        OWLClass subject = df.getOWLClass(IRI.create("http://example.org/annval#A"));
        m.addAxiom(o, df.getOWLDeclarationAxiom(subject));
        m.addAxiom(o, df.getOWLAnnotationAssertionAxiom(df.getRDFSSeeAlso(), subject.getIRI(),
                IRI.create("http://example.org/annval#B")));

        // A name in this position carries no kind, so OWL does not treat it as a term at all.
        // The check cannot see it, which is an accepted limit rather than a fault.
        assertFalse(names(checker.check(o)).contains("http://example.org/annval#B"));
        assertTrue(checker.check(o).isEmpty());
    }

    @Test
    public void shouldClassifyThirdPartyVocabularyAsAWarningAndOwnVocabularyAsAnError() throws Exception {
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = m.getOWLDataFactory();
        OWLOntology o = m.createOntology(IRI.create("http://example.org/mixed"));
        OWLClass subject = df.getOWLClass(IRI.create("http://example.org/mixed#A"));
        OWLAnnotationProperty theirs =
                df.getOWLAnnotationProperty(IRI.create("http://purl.org/dc/terms/title"));
        OWLAnnotationProperty mine =
                df.getOWLAnnotationProperty(IRI.create("http://example.org/mixed#note"));
        m.addAxiom(o, df.getOWLDeclarationAxiom(subject));
        m.addAxiom(o, df.getOWLAnnotationAssertionAxiom(theirs, subject.getIRI(), df.getOWLLiteral("T")));
        m.addAxiom(o, df.getOWLAnnotationAssertionAxiom(mine, subject.getIRI(), df.getOWLLiteral("N")));

        MissingDeclarationReport report = checker.check(o);

        // OWL requires both declarations. They differ only in who is able to add them.
        assertEquals(ImmutableList.of("http://example.org/mixed#note"),
                names(report.getFindings(DeclarationSeverity.ERROR)));
        assertEquals(ImmutableList.of("http://purl.org/dc/terms/title"),
                names(report.getFindings(DeclarationSeverity.WARNING)));
    }

    @Test
    public void shouldClassifyEveryListedThirdPartyVocabularyAsAWarning() throws Exception {
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = m.getOWLDataFactory();
        OWLOntology o = m.createOntology(IRI.create("http://example.org/borrowed"));
        OWLClass subject = df.getOWLClass(IRI.create("http://example.org/borrowed#A"));
        m.addAxiom(o, df.getOWLDeclarationAxiom(subject));
        String[] borrowed = {
                "http://purl.org/dc/terms/title",
                "http://www.w3.org/2004/02/skos/core#prefLabel",
                "http://www.geneontology.org/formats/oboInOwl#hasOBONamespace",
                "http://xmlns.com/foaf/0.1/name" };
        for (String property : borrowed) {
            m.addAxiom(o, df.getOWLAnnotationAssertionAxiom(
                    df.getOWLAnnotationProperty(IRI.create(property)),
                    subject.getIRI(), df.getOWLLiteral("v")));
        }

        MissingDeclarationReport report = checker.check(o);

        assertEquals(4, report.getFindings(DeclarationSeverity.WARNING).size());
        assertTrue(report.getFindings(DeclarationSeverity.ERROR).isEmpty());
    }

    @Test
    public void shouldClassifyAnOboTermAsAnErrorBecauseItsNamespaceIsNotThirdParty() throws Exception {
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = m.getOWLDataFactory();
        OWLOntology o = m.createOntology(IRI.create("http://purl.obolibrary.org/obo/mine.owl"));
        OWLClass declared = df.getOWLClass(IRI.create("http://purl.obolibrary.org/obo/MINE_0000001"));
        OWLClass missing = df.getOWLClass(IRI.create("http://purl.obolibrary.org/obo/GO_0008150"));
        m.addAxiom(o, df.getOWLDeclarationAxiom(declared));
        m.addAxiom(o, df.getOWLSubClassOfAxiom(declared, missing));

        MissingDeclarationReport report = checker.check(o);

        // Every OBO ontology puts its terms in this one namespace, and no ontology owns it.
        // Treating that as borrowed vocabulary would demote every OBO term to a warning.
        assertEquals(1, report.size());
        assertEquals(DeclarationSeverity.ERROR, report.getFindings().get(0).getSeverity());
    }

    @Test
    public void shouldLeaveEveryOntologyInTheClosureUnchanged() throws Exception {
        OWLOntology leaf = ClosureFixtures.threeLevelClosure();
        Map<OWLOntology, Set<OWLAxiom>> before = axiomsByOntology(leaf);

        checker.check(leaf);

        // The check reads only. Nothing is added, and in particular the declarations it
        // reports as missing are not quietly supplied.
        assertEquals(before, axiomsByOntology(leaf));
    }

    private static Map<OWLOntology, Set<OWLAxiom>> axiomsByOntology(OWLOntology root) {
        Map<OWLOntology, Set<OWLAxiom>> axioms = new HashMap<>();
        for (OWLOntology o : root.getImportsClosure()) {
            axioms.put(o, new HashSet<>(o.getAxioms()));
        }
        return axioms;
    }

    private static OWLOntology manyUndeclaredTerms() throws Exception {
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = m.getOWLDataFactory();
        OWLOntology o = m.createOntology(IRI.create("http://example.org/many"));
        OWLClass anchor = df.getOWLClass(IRI.create("http://example.org/many#Anchor"));
        m.addAxiom(o, df.getOWLDeclarationAxiom(anchor));
        for (int i = 0; i < 40; i++) {
            OWLClass missing = df.getOWLClass(IRI.create(String.format("http://example.org/many#M%02d", i)));
            m.addAxiom(o, df.getOWLSubClassOfAxiom(anchor, missing));
        }
        return o;
    }

    private static List<String> names(MissingDeclarationReport report) {
        return names(report.getFindings());
    }

    private static List<String> names(List<MissingDeclarationFinding> findings) {
        return findings.stream()
                .map(f -> f.getEntity().getIRI().toString())
                .collect(Collectors.toList());
    }
}
