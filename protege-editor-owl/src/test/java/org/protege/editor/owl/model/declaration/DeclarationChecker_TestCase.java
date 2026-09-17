package org.protege.editor.owl.model.declaration;

import org.junit.Test;
import org.semanticweb.owlapi.model.OWLOntology;

import java.util.List;
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

        DeclarationReport report = new DeclarationChecker().check(leaf);

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

        new DeclarationChecker(missing, misplaced).check(ClosureFixtures.threeLevelClosure());

        assertNotNull(missing.index);
        assertSame("both checks must share one walk of the closure", missing.index, misplaced.index);
    }

    @Test
    public void shouldReportNothingForAClosureWithNoDefects() throws Exception {
        DeclarationReport report = new DeclarationChecker().check(ClosureFixtures.componentLayoutClosure());

        assertTrue(report.isEmpty());
        assertTrue(report.getMissing().isEmpty());
        assertTrue(report.getMisplaced().isEmpty());
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
