package org.protege.editor.owl.model.declaration;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/**
 * Tests the findings produced by {@link MisplacedDeclarationChecker}.
 */
public class MisplacedDeclarationChecker_TestCase {

    private static final String OBO = "http://purl.obolibrary.org/obo/";

    private final MisplacedDeclarationChecker checker =
            new MisplacedDeclarationChecker(OwnershipRules::registered);

    // Correct work: nothing to report.

    @Test
    public void shouldNotFlagAnEditingDocumentImportingItsOwnComponents() throws Exception {
        // The tripwire for the fourth condition. Without it, every term of a project that keeps
        // its terms in component documents is reported.
        assertTrue(checker.check(ClosureFixtures.componentLayoutClosure()).isEmpty());
    }

    @Test
    public void shouldNotFlagAGeneratedImportModuleWhoseOwnerIsAbsent() throws Exception {
        assertTrue(checker.check(ClosureFixtures.absentOwnerClosure()).isEmpty());
    }

    @Test
    public void shouldNotFlagAReleaseCarryingForeignDeclarationsBesideTheirOwner() throws Exception {
        assertTrue(checker.check(ClosureFixtures.declaringOwnerClosure()).isEmpty());
    }

    @Test
    public void shouldNotFlagATermWhoseIdSpaceMatchesNothingLoaded() throws Exception {
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = m.getOWLDataFactory();
        OWLOntology mine = m.createOntology(IRI.create(OBO + "mine.owl"));
        m.addAxiom(mine, df.getOWLDeclarationAxiom(df.getOWLClass(IRI.create(OBO + "ZZZZ_0000001"))));

        assertTrue(checker.check(mine).isEmpty());
    }

    @Test
    public void shouldNeverReportStandardVocabulary() throws Exception {
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = m.getOWLDataFactory();
        // An ontology named for the OWL namespace would otherwise be owl:Thing's owner.
        IRI owlIri = IRI.create("http://www.w3.org/2002/07/owl");
        m.createOntology(owlIri);
        OWLOntology mine = m.createOntology(IRI.create("http://example.org/mine"));
        m.applyChange(new AddImport(mine, df.getOWLImportsDeclaration(owlIri)));
        m.addAxiom(mine, df.getOWLDeclarationAxiom(df.getOWLThing()));

        assertTrue(checker.check(mine).isEmpty());
    }

    @Test
    public void shouldLeaveAnEntityNothingDeclaresToTheMissingCheck() throws Exception {
        OWLOntology leaf = ClosureFixtures.threeLevelClosure();
        OWLClass ghost = leaf.getOWLOntologyManager().getOWLDataFactory()
                .getOWLClass(IRI.create(ClosureFixtures.MID + "#Ghost"));

        assertFalse(entitiesIn(checker.check(leaf)).contains(ghost));
        assertTrue(new MissingDeclarationChecker().check(leaf).getFindings().stream()
                .anyMatch(finding -> finding.getEntity().equals(ghost)));
    }

    // The real defect.

    @Test
    public void shouldFlagADeclarationPlacedOutsideTheOntologyOwningItsNamespace() throws Exception {
        OWLOntology leaf = ClosureFixtures.threeLevelClosure();

        MisplacedDeclarationReport report = checker.check(leaf);

        assertEquals(ImmutableList.of(ClosureFixtures.BASE + "#Misplaced"), names(report));
        assertEquals(NamespaceRule.ID, report.getFindings().get(0).getOwnershipRuleId());
    }

    @Test
    public void shouldFlagATermAttributedToAnOntologyThatDoesNotDefineIt() throws Exception {
        MisplacedDeclarationReport report = checker.check(ClosureFixtures.oboClosure());

        assertEquals(ImmutableList.of(OBO + "GO_0006915"), names(report));
        assertEquals(OboIdentifierRule.ID, report.getFindings().get(0).getOwnershipRuleId());
    }

    @Test
    public void shouldFlagASiblingProjectDeclaringAnotherProjectsTerm() throws Exception {
        assertEquals(ImmutableList.of(OBO + "GO_0006915"),
                names(checker.check(ClosureFixtures.siblingProjectClosure())));
    }

    @Test
    public void shouldFlagATermWhoseIdSpaceContainsAnUnderscore() throws Exception {
        MisplacedDeclarationReport report = checker.check(ClosureFixtures.underscoredPrefixClosure());

        assertEquals(ImmutableList.of(OBO + "APOLLO_SV_0000001"), names(report));
        assertEquals(IRI.create(OBO + "apollo_sv.owl"),
                report.getFindings().get(0).getOwningOntology().getOntologyIRI().get());
    }

    @Test
    public void shouldFlagATermWhoseIdSpaceIsMixedCase() throws Exception {
        MisplacedDeclarationReport report = checker.check(ClosureFixtures.mixedCasePrefixClosure());

        assertEquals(ImmutableList.of(OBO + "NCBITaxon_9606"), names(report));
        assertEquals(IRI.create(OBO + "ncbitaxon.owl"),
                report.getFindings().get(0).getOwningOntology().getOntologyIRI().get());
    }

    @Test
    public void shouldFlagAnImportModuleBelongingToAnotherProject() throws Exception {
        // cl/imports/go_import.owl is in CL's family, not GO's, so it is a foreign declarer.
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = m.getOWLDataFactory();
        IRI goIri = IRI.create(OBO + "go.owl");
        OWLOntology go = m.createOntology(goIri);
        m.addAxiom(go, df.getOWLDeclarationAxiom(df.getOWLClass(IRI.create(OBO + "GO_0008150"))));
        IRI clImportIri = IRI.create(OBO + "cl/imports/go_import.owl");
        OWLOntology clImport = m.createOntology(clImportIri);
        m.addAxiom(clImport, df.getOWLDeclarationAxiom(df.getOWLClass(IRI.create(OBO + "GO_0006915"))));
        OWLOntology cl = m.createOntology(IRI.create(OBO + "cl.owl"));
        m.applyChange(new AddImport(cl, df.getOWLImportsDeclaration(goIri)));
        m.applyChange(new AddImport(cl, df.getOWLImportsDeclaration(clImportIri)));

        assertEquals(ImmutableList.of(OBO + "GO_0006915"), names(checker.check(cl)));
    }

    // What a finding says.

    @Test
    public void shouldReportAtWarningEvenInTheAuthorsOwnNamespace() throws Exception {
        OWLOntology leaf = ClosureFixtures.threeLevelClosure();
        MisplacedDeclarationFinding finding = checker.check(leaf).getFindings().get(0);

        assertEquals(DeclarationSeverity.WARNING, finding.getSeverity());
        assertEquals(DeclarationSeverity.ERROR, DeclarationSeverity.of(finding.getEntity()));
    }

    @Test
    public void shouldNameTheEntityTheOwnerTheDeclarersAndTheRule() throws Exception {
        OWLOntology leaf = ClosureFixtures.threeLevelClosure();
        MisplacedDeclarationFinding finding = checker.check(leaf).getFindings().get(0);

        assertEquals(IRI.create(ClosureFixtures.BASE + "#Misplaced"), finding.getEntity().getIRI());
        assertEquals(EntityType.CLASS, finding.getEntityType());
        assertEquals(IRI.create(ClosureFixtures.BASE),
                finding.getOwningOntology().getOntologyIRI().get());
        assertEquals(ImmutableSet.of(IRI.create(ClosureFixtures.LEAF)),
                finding.getDeclaringOntologies().stream()
                        .map(id -> id.getOntologyIRI().get())
                        .collect(Collectors.toSet()));
        assertEquals(NamespaceRule.ID, finding.getOwnershipRuleId());
    }

    @Test
    public void shouldJudgeAPunnedIdentifierSeparatelyForEachKind() throws Exception {
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = m.getOWLDataFactory();
        IRI duo = IRI.create("http://example.org/base#Duo");
        IRI baseIri = IRI.create("http://example.org/base");
        OWLOntology base = m.createOntology(baseIri);
        m.addAxiom(base, df.getOWLDeclarationAxiom(df.getOWLClass(duo)));
        OWLOntology other = m.createOntology(IRI.create("http://example.org/other"));
        m.applyChange(new AddImport(other, df.getOWLImportsDeclaration(baseIri)));
        m.addAxiom(other, df.getOWLDeclarationAxiom(df.getOWLNamedIndividual(duo)));

        MisplacedDeclarationReport report = checker.check(other);

        assertEquals(1, report.size());
        assertEquals(EntityType.NAMED_INDIVIDUAL, report.getFindings().get(0).getEntityType());
    }

    // Result shape and the settings.

    @Test
    public void shouldReturnAnEmptyReportRatherThanNull() throws Exception {
        MisplacedDeclarationReport report = checker.check(ClosureFixtures.componentLayoutClosure());

        assertNotNull(report);
        assertTrue(report.isEmpty());
        assertEquals(0, report.size());
    }

    @Test
    public void shouldReturnTheSameFindingsInTheSameOrderOnRepeatedRuns() throws Exception {
        OWLOntology root = twoRuleClosure();

        assertEquals(names(checker.check(root)), names(checker.check(root)));
        assertEquals(ImmutableList.of("http://example.org/base#Misplaced", OBO + "GO_0006915"),
                names(checker.check(root)));
    }

    @Test
    public void shouldNotModifyAnyOntologyInTheClosure() throws Exception {
        OWLOntology root = twoRuleClosure();
        Map<OWLOntology, Set<OWLAxiom>> before = axiomsOf(root);

        checker.check(root);

        assertEquals(before, axiomsOf(root));
    }

    @Test
    public void shouldDropOnlyTheFindingsOfTheRuleThatIsSwitchedOff() throws Exception {
        OWLOntology root = twoRuleClosure();

        assertEquals(ImmutableList.of(OBO + "GO_0006915"),
                names(checkerWith(new OboIdentifierRule()).check(root)));
        assertEquals(ImmutableList.of("http://example.org/base#Misplaced"),
                names(checkerWith(new NamespaceRule()).check(root)));
    }

    @Test
    public void shouldReportNothingWhenBothRulesAreSwitchedOff() throws Exception {
        OWLOntology root = twoRuleClosure();

        assertTrue(checkerWith().check(root).isEmpty());
        // The missing-declaration findings are unaffected by the misplaced rules.
        assertFalse(new MissingDeclarationChecker().check(root).isEmpty());
    }

    @Test
    public void shouldReportAgainstWhateverOwnerARuleDecides() throws Exception {
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = m.getOWLDataFactory();
        OWLOntology owner = m.createOntology(IRI.create("http://example.org/owner"));
        OWLOntology declarer = m.createOntology(IRI.create("http://example.org/declarer"));
        m.applyChange(new AddImport(declarer,
                df.getOWLImportsDeclaration(IRI.create("http://example.org/owner"))));
        OWLClass term = df.getOWLClass(IRI.create("http://example.org/somewhere/Term"));
        m.addAxiom(declarer, df.getOWLDeclarationAxiom(term));

        // Neither shipped rule can own this IRI, so only the stand-in rule can produce a finding.
        MisplacedDeclarationReport report =
                checkerWith(standInRuleOwning(owner.getOntologyID())).check(declarer);

        assertEquals(ImmutableList.of("http://example.org/somewhere/Term"), names(report));
        assertEquals("misplaced.rule.use.stand.in",
                report.getFindings().get(0).getOwnershipRuleId());
    }

    @Test
    public void shouldReadTheRulesAfreshOnEveryRun() throws Exception {
        OWLOntology root = twoRuleClosure();
        AtomicReference<ImmutableList<OwnershipRule>> rules =
                new AtomicReference<>(OwnershipRules.registered());
        MisplacedDeclarationChecker rereading = new MisplacedDeclarationChecker(rules::get);

        assertEquals(2, rereading.check(root).size());
        rules.set(ImmutableList.of(new NamespaceRule()));
        assertEquals("a cached value would need a restart to take effect",
                1, rereading.check(root).size());
    }

    /**
     * Creates an ontology with one finding from each ownership rule.
     *
     * @return the root ontology importing all four documents
     * @throws Exception if an ontology in the fixture cannot be created
     */
    private static OWLOntology twoRuleClosure() throws Exception {
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = m.getOWLDataFactory();
        IRI goIri = IRI.create(OBO + "go.owl");
        m.createOntology(goIri);
        IRI uberonIri = IRI.create(OBO + "uberon.owl");
        OWLOntology uberon = m.createOntology(uberonIri);
        m.addAxiom(uberon, df.getOWLDeclarationAxiom(df.getOWLClass(IRI.create(OBO + "GO_0006915"))));

        IRI baseIri = IRI.create("http://example.org/base");
        m.createOntology(baseIri);
        IRI otherIri = IRI.create("http://example.org/other");
        OWLOntology other = m.createOntology(otherIri);
        OWLClass misplaced = df.getOWLClass(IRI.create("http://example.org/base#Misplaced"));
        m.addAxiom(other, df.getOWLDeclarationAxiom(misplaced));
        m.addAxiom(other, df.getOWLSubClassOfAxiom(misplaced,
                df.getOWLClass(IRI.create("http://example.org/base#Absent"))));

        OWLOntology root = m.createOntology(IRI.create("http://example.org/root"));
        for (IRI imported : new IRI[]{goIri, uberonIri, baseIri, otherIri}) {
            m.applyChange(new AddImport(root, df.getOWLImportsDeclaration(imported)));
        }
        return root;
    }

    private static MisplacedDeclarationChecker checkerWith(OwnershipRule... rules) {
        ImmutableList<OwnershipRule> enabled = ImmutableList.copyOf(rules);
        return new MisplacedDeclarationChecker(() -> enabled);
    }

    /** An ownership rule that names one ontology as the owner of every entity. */
    private static OwnershipRule standInRuleOwning(OWLOntologyID owner) {
        return new OwnershipRule() {
            @Override
            public String getId() {
                return "misplaced.rule.use.stand.in";
            }

            @Override
            public OwnershipRuleDisplay getDisplay() {
                return OwnershipRuleDisplay.get("Stand-in rule",
                        "<html>Names one ontology as the owner of everything."
                                + "<br><br>Used by tests only.</html>");
            }

            @Override
            public Resolver compile(Collection<OWLOntologyID> ontologies) {
                return entity -> Optional.of(owner);
            }
        };
    }

    private static List<String> names(MisplacedDeclarationReport report) {
        return report.getFindings().stream()
                .map(finding -> finding.getEntity().getIRI().toString())
                .collect(Collectors.toList());
    }

    private static Set<OWLEntity> entitiesIn(MisplacedDeclarationReport report) {
        return report.getFindings().stream()
                .map(MisplacedDeclarationFinding::getEntity)
                .collect(Collectors.toSet());
    }

    private static Map<OWLOntology, Set<OWLAxiom>> axiomsOf(OWLOntology root) {
        Map<OWLOntology, Set<OWLAxiom>> axioms = new HashMap<>();
        for (OWLOntology ontology : root.getImportsClosure()) {
            axioms.put(ontology, ImmutableSet.copyOf(ontology.getAxioms()));
        }
        return axioms;
    }
}
