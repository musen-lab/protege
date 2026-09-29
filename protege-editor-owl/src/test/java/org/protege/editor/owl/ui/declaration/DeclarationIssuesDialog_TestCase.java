package org.protege.editor.owl.ui.declaration;

import org.junit.Test;
import org.protege.editor.owl.model.declaration.DeclarationReportOutline;

import javax.swing.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;
import static org.protege.editor.owl.model.declaration.DeclarationReportFixtures.report;
import static org.protege.editor.owl.model.declaration.DeclarationReportFixtures.reportWithMisplacedDisabled;

/**
 * Tests the message of the warning shown after a save, and what each way out of it does.
 */
public class DeclarationIssuesDialog_TestCase {

    @Test
    public void shouldListBothHeadings() {
        String message = DeclarationIssuesDialog.warningMessageContentOf(DeclarationReportOutline.of(report(9, 3, 3)));

        assertEquals("<html>The ontology was saved with \"Preferences &gt; Axioms &gt; Suppress automatic entity "
                + "declarations\" option enabled,<br>"
                + "and the following issues remain:<br>"
                + "<br>"
                + "&nbsp;• Missing entity declarations: 12<br>"
                + "&nbsp;• Misplaced entity declarations: 3<br>"
                + "<br>"
                + "See the Protégé log for details.<br>"
                + "<br>"
                + "<b>Warning: Protégé may not load ontologies with "
                + "missing entity declarations correctly.</b></html>", message);
    }

    @Test
    public void shouldListAMisplacedCheckThatDidNotRunAsDisabled() {
        String message = DeclarationIssuesDialog.warningMessageContentOf(
                DeclarationReportOutline.of(reportWithMisplacedDisabled(9, 3)));

        assertTrue(message.contains("&nbsp;• Missing entity declarations: 12<br>"
                + "&nbsp;• Misplaced entity declarations: &lt;detection disabled&gt;<br>"));
    }

    @Test
    public void shouldShowTheSameHeadingsTheOutlineGivesTheLog() {
        DeclarationReportOutline outline = DeclarationReportOutline.of(report(2043, 0, 5));
        String message = DeclarationIssuesDialog.warningMessageContentOf(outline);

        for (DeclarationReportOutline.Section section : outline.getSections()) {
            assertTrue(message.contains("&nbsp;• " + section.getHeading() + "<br>"));
        }
    }

    @Test
    public void shouldOpenTheLogWhenShowLogIsChosen() {
        assertEquals(1, logOpeningsAfterChoosing(0));
    }

    @Test
    public void shouldDoNothingWhenOkIsChosen() {
        assertEquals(0, logOpeningsAfterChoosing(1));
    }

    @Test
    public void shouldDoNothingWhenTheWarningIsClosed() {
        assertEquals(0, logOpeningsAfterChoosing(JOptionPane.CLOSED_OPTION));
    }

    @Test
    public void shouldHandTheChooserTheMessage() {
        DeclarationReportOutline outline = DeclarationReportOutline.of(report(1, 0, 0));
        AtomicReference<String> shown = new AtomicReference<>();

        DeclarationIssuesDialog.show(outline, message -> {
            shown.set(message);
            return 1;
        }, () -> {});

        assertEquals(DeclarationIssuesDialog.warningMessageContentOf(outline), shown.get());
    }

    private static int logOpeningsAfterChoosing(int option) {
        AtomicInteger openings = new AtomicInteger();
        DeclarationIssuesDialog.show(DeclarationReportOutline.of(report(1, 0, 0)),
                message -> option, openings::incrementAndGet);
        return openings.get();
    }
}
