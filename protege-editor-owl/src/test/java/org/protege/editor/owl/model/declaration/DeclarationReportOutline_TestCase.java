package org.protege.editor.owl.model.declaration;

import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.protege.editor.owl.model.declaration.DeclarationReportFixtures.*;
import static org.junit.Assert.*;

/**
 * Tests the sections and wording that every presentation of a declaration check result shares.
 */
public class DeclarationReportOutline_TestCase {

    @Test
    public void shouldHeadBothSectionsWithTheirCountsInFixedOrder() {
        DeclarationReportOutline outline = DeclarationReportOutline.of(report(9, 3, 3));

        assertEquals(ImmutableList.of("Missing entity declarations: 12", "Misplaced entity declarations: 3"),
                headingsOf(outline));
        assertEquals(ImmutableList.of("Missing entity declarations", "Misplaced entity declarations"),
                outline.getSections().stream().map(DeclarationReportOutline.Section::getTitle)
                        .collect(Collectors.toList()));
        assertEquals(12, outline.getMissingSection().getCount());
        assertEquals(3, outline.getMisplacedSection().getCount());
        assertTrue(outline.hasFindings());
    }

    @Test
    public void shouldShowZeroForACheckThatRanAndFoundNothing() {
        DeclarationReportOutline outline = DeclarationReportOutline.of(report(9, 3, 0));

        assertEquals(ImmutableList.of("Missing entity declarations: 12", "Misplaced entity declarations: 0"),
                headingsOf(outline));
        assertEquals(DeclarationCheckStatus.EXECUTED, outline.getMisplacedSection().getStatus());
    }

    @Test
    public void shouldShowDetectionDisabledForACheckThatDidNotRun() {
        DeclarationReportOutline outline = DeclarationReportOutline.of(reportWithMisplacedDisabled(9, 3));

        assertEquals("Misplaced entity declarations: <detection disabled>",
                outline.getMisplacedSection().getHeading());
        assertEquals(DeclarationCheckStatus.MISPLACED_SKIPPED,
                outline.getMisplacedSection().getStatus());
        assertTrue(outline.hasFindings());
    }

    @Test
    public void shouldShowDetectionDisabledWhateverTheReason() {
        DeclarationReportOutline outline = DeclarationReportOutline.of(DeclarationReport.get(
                MissingDeclarationReport.skipped(DeclarationCheckStatus.SKIPPED),
                MisplacedDeclarationReport.skipped(DeclarationCheckStatus.SKIPPED)));

        assertEquals(ImmutableList.of("Missing entity declarations: <detection disabled>",
                        "Misplaced entity declarations: <detection disabled>"),
                headingsOf(outline));
    }

    @Test
    public void shouldHaveNothingToReportWhenBothChecksFoundNothing() {
        DeclarationReportOutline outline = DeclarationReportOutline.of(report(0, 0, 0));

        assertFalse(outline.hasFindings());
        assertEquals(2, outline.getSections().size());
    }

    @Test
    public void shouldHaveNothingToReportWhenOneCheckFoundNothingAndTheOtherDidNotRun() {
        assertFalse(DeclarationReportOutline.of(reportWithMisplacedDisabled(0, 0)).hasFindings());
    }

    @Test
    public void shouldSeparateThousandsInACount() {
        DeclarationReportOutline outline = DeclarationReportOutline.of(report(2043, 0, 0));

        assertEquals("Missing entity declarations: 2,043", outline.getMissingSection().getHeading());
    }

    @Test
    public void shouldHandTheFindingsToRenderersInTheirResultOrder() {
        DeclarationReport report = report(2, 1, 2);
        DeclarationReportOutline outline = DeclarationReportOutline.of(report);

        assertEquals(report.getMissing().getFindings(), outline.getMissing());
        assertEquals(report.getMisplaced().getFindings(), outline.getMisplaced());
    }

    @Test
    public void shouldNameTheRuleOfAMisplacedFindingByItsLabel() {
        MisplacedDeclarationFinding finding = misplaced(1).getFindings().get(0);

        assertEquals(new OboIdentifierRule().getDisplay().getLabel(),
                DeclarationReportOutline.ruleLabelOf(finding));
    }

    @Test
    public void shouldFallBackToTheRuleIdWhenNoRegisteredRuleHasIt() {
        MisplacedDeclarationFinding finding = MisplacedDeclarationFinding.get(
                OWLManager.getOWLDataFactory().getOWLClass(IRI.create("http://example.org/x#A")),
                GO, Collections.singleton(PIZZA), "retired-rule");

        assertEquals("retired-rule", DeclarationReportOutline.ruleLabelOf(finding));
    }

    private static List<String> headingsOf(DeclarationReportOutline outline) {
        return outline.getSections().stream()
                .map(DeclarationReportOutline.Section::getHeading)
                .collect(Collectors.toList());
    }
}
