package org.protege.editor.owl.model.declaration;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.*;

/**
 * Verifies the check status that a misplaced-declaration report records.
 */
public class MisplacedDeclarationReport_TestCase {

    @Test
    public void shouldRecordThatACheckWhichFoundNothingRan() {
        MisplacedDeclarationReport empty = MisplacedDeclarationReport.get(Collections.emptyList());

        assertEquals(DeclarationCheckStatus.EXECUTED, empty.getStatus());
        assertTrue(empty.isEmpty());
    }

    @Test
    public void shouldRecordEachReasonACheckDidNotRunAndHoldNoFindings() {
        for (DeclarationCheckStatus reason : new DeclarationCheckStatus[]{
                DeclarationCheckStatus.SKIPPED,
                DeclarationCheckStatus.MISPLACED_SKIPPED}) {
            MisplacedDeclarationReport skipped = MisplacedDeclarationReport.skipped(reason);

            assertEquals(reason, skipped.getStatus());
            assertTrue(skipped.isEmpty());
            assertEquals(0, skipped.size());
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRefuseANotCheckedReportWithoutAReason() {
        MisplacedDeclarationReport.skipped(DeclarationCheckStatus.EXECUTED);
    }
}
