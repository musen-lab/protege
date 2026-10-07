package org.protege.editor.owl.ui.declaration;

import org.junit.Test;
import org.protege.editor.core.ui.list.MList;
import org.protege.editor.core.ui.list.MListSectionHeader;
import org.protege.editor.owl.model.declaration.DeclarationReportOutline;
import org.protege.editor.owl.model.declaration.MisplacedDeclarationFinding;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
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
        assertEquals(Arrays.asList(
                        "Severity: " + finding.getSeverity(),
                        "Ownership rule: " + DeclarationReportOutline.ruleLabelOf(finding),
                        "Owned by: http://purl.obolibrary.org/obo/go.owl",
                        "Declared in: http://example.org/pizza"),
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
        assertEquals(Arrays.asList("Severity: ERROR", "Used in: http://example.org/pizza"), errorRow.getDetail());
        assertEquals(Arrays.asList("Severity: WARNING", "Used in: http://example.org/pizza"), warningRow.getDetail());
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

    @Test
    public void shouldShowEveryMisplacedDetailLineWithinTheDefaultDialogWidth() throws Exception {
        // MList resets its cell height cache from resize events, so lay out on the event thread.
        SwingUtilities.invokeAndWait(this::shouldShowEveryMisplacedDetailLineWithinTheDefaultDialogWidthOnEdt);
    }

    private void shouldShowEveryMisplacedDetailLineWithinTheDefaultDialogWidthOnEdt() {
        DeclarationReportOutline outline = DeclarationReportOutline.of(report(1, 1, 1));
        DeclarationCheckReportPanel panel =
                new DeclarationCheckReportPanel(new DefaultListCellRenderer(), outline);
        panel.setSize(panel.getPreferredSize());
        layOut(panel);

        JViewport viewport = findAll(panel, JViewport.class).get(0);
        MList list = findAll(panel, MList.class).get(0);
        int misplacedRowIndex = 4; // header, 2 missing rows, header, misplaced row
        Rectangle rowBounds = list.getCellBounds(misplacedRowIndex, misplacedRowIndex);
        Component row = list.getCellRenderer().getListCellRendererComponent(
                list, list.getModel().getElementAt(misplacedRowIndex), misplacedRowIndex, false, false);
        row.setSize(rowBounds.getSize());
        layOut(row);

        List<String> detailTexts = new ArrayList<>();
        for (JLabel label : findAll(row, JLabel.class)) {
            detailTexts.add(label.getText());
            Rectangle labelBounds = SwingUtilities.convertRectangle(label.getParent(), label.getBounds(), row);
            assertTrue("'" + label.getText() + "' is clipped by its row",
                    labelBounds.height >= label.getPreferredSize().height
                            && labelBounds.y + labelBounds.height <= rowBounds.height);
        }
        assertTrue("the report needs horizontal scrolling at the default size",
                list.getPreferredSize().width <= viewport.getExtentSize().width);
        assertTrue(detailTexts.contains("Declared in: http://example.org/pizza"));
    }

    @Test
    public void shouldKeepTheListWithinTheViewportAcrossRepeatedLayouts() throws Exception {
        DeclarationReportOutline outline = DeclarationReportOutline.of(report(1, 1, 1));
        // Like OWLCellRenderer, size the entity to the width of the list's parent.
        ListCellRenderer parentWidthRenderer = new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(
                        list, value, index, isSelected, cellHasFocus);
                label.setPreferredSize(null);
                Dimension size = label.getPreferredSize();
                label.setPreferredSize(new Dimension(list.getParent().getWidth(), size.height));
                return label;
            }
        };
        DeclarationCheckReportPanel panel = new DeclarationCheckReportPanel(parentWidthRenderer, outline);
        JScrollPane scrollPane = findAll(panel, JScrollPane.class).get(0);
        MList list = findAll(panel, MList.class).get(0);

        for (Dimension size : Arrays.asList(new Dimension(800, 500), new Dimension(1000, 600),
                new Dimension(700, 500), new Dimension(800, 500))) {
            // Each pass runs on the event thread and then lets the resize events queued by the
            // layout run, as they would in the dialog: MList resets its cell cache on resize.
            for (int pass = 0; pass < 3; pass++) {
                SwingUtilities.invokeAndWait(() -> {
                    panel.setSize(size);
                    layOut(panel);
                });
            }
            SwingUtilities.invokeAndWait(() -> {
                int viewportWidth = scrollPane.getViewport().getExtentSize().width;
                assertTrue("list width " + list.getWidth() + " exceeds viewport width " + viewportWidth,
                        list.getWidth() <= viewportWidth);
                assertFalse("the report shows a horizontal scrollbar at " + size,
                        scrollPane.getHorizontalScrollBar().isVisible());
            });
        }
    }

    private static void layOut(Component component) {
        component.doLayout();
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                layOut(child);
            }
        }
    }

    private static <T extends Component> List<T> findAll(Component root, Class<T> type) {
        List<T> found = new ArrayList<>();
        if (type.isInstance(root)) {
            found.add(type.cast(root));
        }
        if (root instanceof Container) {
            for (Component child : ((Container) root).getComponents()) {
                found.addAll(findAll(child, type));
            }
        }
        return found;
    }
}
