package org.protege.editor.owl.model.declaration;

import org.junit.Test;
import org.semanticweb.owlapi.model.OWLOntology;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static java.util.Collections.singletonList;
import static org.junit.Assert.*;

/**
 * Tests the combined declaration checker.
 */
public class DeclarationChecker_TestCase {

    @Test
    public void shouldGiveTheSameFindingsAsEachCheckRunAlone() throws Exception {
        OWLOntology leaf = ClosureFixtures.threeLevelClosure();

        DeclarationReport report = checkerThatRuns().check(leaf);

        assertEquals(new MissingDeclarationChecker().check(leaf).getFindings(),
                report.getMissing().getFindings());
        assertEquals(entityNames(new MisplacedDeclarationChecker().check(leaf)),
                entityNames(report.getMisplaced()));
        assertEquals(singletonList(ClosureFixtures.BASE + "#Misplaced"),
                entityNames(report.getMisplaced()));
    }

    @Test
    public void shouldBuildOneIndexAndHandItToBothChecks() throws Exception {
        RecordingMissingChecker missing = new RecordingMissingChecker();
        RecordingMisplacedChecker misplaced = new RecordingMisplacedChecker();

        new DeclarationChecker(missing, misplaced, () -> true)
                .check(ClosureFixtures.threeLevelClosure());

        assertNotNull(missing.index);
        assertSame("both checks must share one walk of the closure", missing.index, misplaced.index);
    }

    @Test
    public void shouldReportNothingForAClosureWithNoDefects() throws Exception {
        DeclarationReport report = checkerThatRuns().check(ClosureFixtures.componentLayoutClosure());

        assertTrue(report.isEmpty());
        assertTrue(report.getMissing().isEmpty());
        assertTrue(report.getMisplaced().isEmpty());
    }

    @Test
    public void shouldReportNothingWhileAutomaticDeclarationsAreWritten() throws Exception {
        OWLOntology leaf = ClosureFixtures.threeLevelClosure();

        // Saving would write a declaration for everything the closure leaves undeclared, so a
        // missing finding would be papered over and the declaration written reported as misplaced.
        DeclarationReport report = checkerSuppressing(false).check(leaf);

        assertTrue(report.isEmpty());
        assertTrue(report.getMissing().isEmpty());
        assertTrue(report.getMisplaced().isEmpty());
    }

    @Test
    public void shouldReportTheSameClosureOnceSuppressionIsOn() throws Exception {
        OWLOntology leaf = ClosureFixtures.threeLevelClosure();

        DeclarationReport report = checkerSuppressing(true).check(leaf);

        assertFalse(report.getMissing().isEmpty());
        assertEquals(singletonList(ClosureFixtures.BASE + "#Misplaced"),
                entityNames(report.getMisplaced()));
    }

    @Test
    public void shouldLeaveEachCheckAnsweringWhenCalledOnItsOwn() throws Exception {
        OWLOntology leaf = ClosureFixtures.threeLevelClosure();

        // The setting governs the feature, not what counts as a finding.
        assertFalse(new MissingDeclarationChecker().check(leaf).isEmpty());
        assertFalse(new MisplacedDeclarationChecker(OwnershipRules::registered).check(leaf).isEmpty());
    }

    @Test
    public void shouldReadTheSettingAfreshOnEveryRun() throws Exception {
        OWLOntology leaf = ClosureFixtures.threeLevelClosure();
        AtomicBoolean suppressing = new AtomicBoolean(false);
        DeclarationChecker checker = new DeclarationChecker(new MissingDeclarationChecker(),
                new MisplacedDeclarationChecker(OwnershipRules::registered), suppressing::get);

        assertTrue(checker.check(leaf).isEmpty());
        suppressing.set(true);
        assertFalse("a cached value would need a restart to take effect",
                checker.check(leaf).isEmpty());
    }

    private static DeclarationChecker checkerThatRuns() {
        return checkerSuppressing(true);
    }

    private static DeclarationChecker checkerSuppressing(boolean suppressing) {
        return new DeclarationChecker(new MissingDeclarationChecker(),
                new MisplacedDeclarationChecker(OwnershipRules::registered), () -> suppressing);
    }

    private static List<String> entityNames(MisplacedDeclarationReport report) {
        return report.getFindings().stream()
                .map(finding -> finding.getEntity().getIRI().toString())
                .collect(Collectors.toList());
    }

    /** Records the index passed to the missing declaration checker. */
    private static class RecordingMissingChecker extends MissingDeclarationChecker {

        private DeclarationIndex index;

        @Override
        MissingDeclarationReport check(DeclarationIndex index) {
            this.index = index;
            return super.check(index);
        }
    }

    /** Records the index passed to the misplaced declaration checker. */
    private static class RecordingMisplacedChecker extends MisplacedDeclarationChecker {

        private DeclarationIndex index;

        RecordingMisplacedChecker() {
            super(OwnershipRules::registered);
        }

        @Override
        MisplacedDeclarationReport check(DeclarationIndex index) {
            this.index = index;
            return super.check(index);
        }
    }
}
