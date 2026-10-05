package org.protege.editor.owl.ui.declaration;

import org.junit.Test;
import org.protege.editor.core.ui.list.MListSectionHeader;
import org.protege.editor.owl.model.declaration.DeclarationReportOutline;
import org.protege.editor.owl.model.declaration.MisplacedDeclarationFinding;

import java.util.List;

import static org.junit.Assert.*;
import static org.protege.editor.owl.model.declaration.DeclarationReportFixtures.report;

/**
 * Tests how the manual declaration report groups findings into list rows.
 */
public class DeclarationCheckReportPanel_TestCase {

    @Test
    public void shouldGroupFindingsUnderTheTwoSectionHeaders() {
        DeclarationReportOutline outline = DeclarationReportOutline.of(report(2, 1, 3));

        List<Object> rows = DeclarationCheckReportPanel.rows(outline);

        // header, 3 missing rows, header, 3 misplaced rows
        assertEquals(8, rows.size());
        assertEquals(outline.getMissingSection().getHeading(), ((MListSectionHeader) rows.get(0)).getName());
        assertEquals("Missing entity declarations: 3", ((MListSectionHeader) rows.get(0)).getName());
        for (int i = 1; i <= 3; i++) {
            assertTrue(rows.get(i) instanceof DeclarationCheckReportPanel.FindingRow);
        }
        assertEquals(outline.getMisplacedSection().getHeading(), ((MListSectionHeader) rows.get(4)).getName());
        assertEquals("Misplaced entity declarations: 3", ((MListSectionHeader) rows.get(4)).getName());
        for (int i = 5; i <= 7; i++) {
            assertTrue(rows.get(i) instanceof DeclarationCheckReportPanel.FindingRow);
        }
    }

    @Test
    public void shouldDescribeMisplacedRowsWithSeverityRuleOwnerAndDeclaringOntologies() {
        DeclarationReportOutline outline = DeclarationReportOutline.of(report(0, 0, 1));
        MisplacedDeclarationFinding finding = outline.getMisplaced().get(0);

        List<Object> rows = DeclarationCheckReportPanel.rows(outline);

        // header(missing), placeholder(missing), header(misplaced), misplaced row
        DeclarationCheckReportPanel.FindingRow misplacedRow =
                (DeclarationCheckReportPanel.FindingRow) rows.get(3);
        assertEquals(finding.getEntity(), misplacedRow.getEntity());
        assertEquals("Severity: " + finding.getSeverity()
                        + " | Ownership rule: " + DeclarationReportOutline.ruleLabelOf(finding)
                        + " | Owned by: http://purl.obolibrary.org/obo/go.owl"
                        + " | Declared in: http://example.org/pizza",
                misplacedRow.getDetail());
    }

    @Test
    public void shouldDescribeMissingRowsWithSeverityAndReferringOntologies() {
        DeclarationReportOutline outline = DeclarationReportOutline.of(report(1, 1, 0));

        List<Object> rows = DeclarationCheckReportPanel.rows(outline);

        DeclarationCheckReportPanel.FindingRow errorRow =
                (DeclarationCheckReportPanel.FindingRow) rows.get(1);
        DeclarationCheckReportPanel.FindingRow warningRow =
                (DeclarationCheckReportPanel.FindingRow) rows.get(2);
        assertEquals("Severity: ERROR | Used in: http://example.org/pizza", errorRow.getDetail());
        assertEquals("Severity: WARNING | Used in: http://example.org/pizza", warningRow.getDetail());
    }

    @Test
    public void shouldShowExplicitPlaceholdersWhenBothSectionsAreEmpty() {
        DeclarationReportOutline outline = DeclarationReportOutline.of(report(0, 0, 0));

        List<Object> rows = DeclarationCheckReportPanel.rows(outline);

        assertEquals(4, rows.size());
        assertEquals("Missing entity declarations: 0", ((MListSectionHeader) rows.get(0)).getName());
        assertEquals(DeclarationCheckReportPanel.NO_MISSING_DECLARATIONS,
                ((DeclarationCheckReportPanel.MessageRow) rows.get(1)).getText());
        assertEquals("Misplaced entity declarations: 0", ((MListSectionHeader) rows.get(2)).getName());
        assertEquals(DeclarationCheckReportPanel.NO_MISPLACED_DECLARATIONS,
                ((DeclarationCheckReportPanel.MessageRow) rows.get(3)).getText());
    }
}
