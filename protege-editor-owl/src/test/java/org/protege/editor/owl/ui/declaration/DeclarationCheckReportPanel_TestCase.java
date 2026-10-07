package org.protege.editor.owl.ui.declaration;

import org.junit.Test;
import org.protege.editor.core.ui.list.MList;
import org.protege.editor.core.ui.list.MListSectionHeader;
import org.protege.editor.owl.model.declaration.*;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;
import static org.protege.editor.owl.model.declaration.DeclarationReportFixtures.report;

/** Tests the two-line dialog presentation independently of checking and log diagnostics. */
public class DeclarationCheckReportPanel_TestCase {

    @Test
    public void shouldGroupFindingsAndKeepEmptySectionPlaceholders() {
        DeclarationReportOutline outline = DeclarationReportOutline.of(report(2, 1, 3));
        List<Object> rows = DeclarationCheckReportPanel.rows(outline, Collections.emptyList());
        assertEquals(8, rows.size());
        assertEquals("Missing entity declarations: 3", ((MListSectionHeader) rows.get(0)).getName());
        assertEquals("Misplaced entity declarations: 3", ((MListSectionHeader) rows.get(4)).getName());
        rows = DeclarationCheckReportPanel.rows(DeclarationReportOutline.of(report(0, 0, 0)), Collections.emptyList());
        assertEquals(4, rows.size());
        assertEquals(DeclarationCheckReportPanel.NO_MISSING_DECLARATIONS,
                ((DeclarationCheckReportPanel.MessageRow) rows.get(1)).getText());
        assertEquals(DeclarationCheckReportPanel.NO_MISPLACED_DECLARATIONS,
                ((DeclarationCheckReportPanel.MessageRow) rows.get(3)).getText());
    }

    @Test
    public void shouldUseOnlyActualLocalMentionsIncludingMultipleOntologies() throws Exception {
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLOntology root = manager.createOntology(IRI.create("http://example.org/root"));
        OWLOntology module = manager.createOntology(IRI.create("http://example.org/module"));
        OWLDataFactory df = manager.getOWLDataFactory();
        manager.applyChange(new AddImport(root, df.getOWLImportsDeclaration(module.getOntologyID().getOntologyIRI().get())));
        OWLClass entity = df.getOWLClass(IRI.create("http://example.org/Undeclared"));
        manager.addAxiom(module, df.getOWLSubClassOfAxiom(entity, df.getOWLThing()));
        // Deliberately supply ancestor-inclusive metadata: the dialog must inspect own signatures.
        MissingDeclarationFinding finding = MissingDeclarationFinding.get(entity,
                Arrays.asList(root.getOntologyID(), module.getOntologyID()));
        DeclarationReportOutline outline = DeclarationReportOutline.of(DeclarationReport.get(
                MissingDeclarationReport.get(Collections.singletonList(finding)),
                MisplacedDeclarationReport.get(Collections.emptyList())));
        assertEquals(Collections.singletonList("Used in: http://example.org/module"),
                findingRow(outline, root.getImportsClosure(), 1).getDetail());
        manager.addAxiom(root, df.getOWLSubClassOfAxiom(entity, df.getOWLThing()));
        assertEquals(Collections.singletonList("Used in: http://example.org/module, http://example.org/root"),
                findingRow(outline, root.getImportsClosure(), 1).getDetail());
        assertEquals(1, new MissingDeclarationChecker().check(root).size());
    }

    @Test
    public void shouldRenderExactlyTwoLabelsWithoutSeverityOrStandaloneRuleForBothMissingSeveritiesAndMisplaced() throws Exception {
        DeclarationReportOutline outline = DeclarationReportOutline.of(report(1, 1, 1));
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLOntology pizza = manager.createOntology(IRI.create("http://example.org/pizza"));
        for (MissingDeclarationFinding finding : outline.getMissing()) {
            manager.addAxiom(pizza, manager.getOWLDataFactory().getOWLDeclarationAxiom(finding.getEntity()));
        }
        SwingUtilities.invokeAndWait(() -> {
            DeclarationCheckReportPanel panel = new DeclarationCheckReportPanel(
                    new DefaultListCellRenderer(), outline, Collections.singletonList(pizza));
            MList list = findAll(panel, MList.class).get(0);
            for (int index : Arrays.asList(1, 2, 4)) {
                Component rendered = list.getCellRenderer().getListCellRendererComponent(
                        list, list.getModel().getElementAt(index), index, false, false);
                List<JLabel> labels = findAll(rendered, JLabel.class);
                assertEquals(2, labels.size());
                String detail = labels.get(1).getText();
                assertEquals(index == 4
                        ? "Suggested placement: http://example.org/pizza → http://purl.obolibrary.org/obo/go.owl"
                        : "Used in: http://example.org/pizza", detail);
                for (JLabel label : labels) {
                    assertFalse(label.getText().contains("Severity:"));
                    assertFalse(label.getText().contains("Ownership rule:"));
                    assertFalse(label.getText().contains("Owned by:"));
                    assertFalse(label.getText().contains("ERROR"));
                    assertFalse(label.getText().contains("WARNING"));
                }
            }
        });
    }

    @Test
    public void shouldRetainAllDeclaringIrisBeforeExpectedOwnerOnOneScrollableLine() throws Exception {
        String longIri = "http://example.org/" + String.join("", Collections.nCopies(100, "long-module-name"));
        MisplacedDeclarationFinding finding = MisplacedDeclarationFinding.get(
                OWLManager.getOWLDataFactory().getOWLClass(IRI.create("http://example.org/QuickBaking")),
                new OWLOntologyID(IRI.create("http://example.org/pizza-fixture/baking")),
                Arrays.asList(new OWLOntologyID(IRI.create("http://example.org/pizza-fixture/menu")),
                        new OWLOntologyID(IRI.create(longIri))), "test-rule");
        DeclarationReportOutline outline = DeclarationReportOutline.of(DeclarationReport.get(
                MissingDeclarationReport.get(Collections.emptyList()),
                MisplacedDeclarationReport.get(Collections.singletonList(finding))));
        String expected = "Suggested placement: http://example.org/pizza-fixture/menu, " + longIri
                + " → http://example.org/pizza-fixture/baking";
        assertEquals(Collections.singletonList(expected), findingRow(outline, Collections.emptyList(), 3).getDetail());
        SwingUtilities.invokeAndWait(() -> {
            DeclarationCheckReportPanel panel = new DeclarationCheckReportPanel(
                    new DefaultListCellRenderer(), outline, Collections.emptyList());
            MList list = findAll(panel, MList.class).get(0);
            for (int width : Arrays.asList(800, 1000, 700, 800)) {
                panel.setSize(width, 500);
                for (int pass = 0; pass < 3; pass++) {
                    layOut(panel);
                }
                JScrollPane scrollPane = findAll(panel, JScrollPane.class).get(0);
                assertTrue(scrollPane.getHorizontalScrollBar().isVisible());
                assertTrue(list.getPreferredSize().width > scrollPane.getViewport().getExtentSize().width);
                Component rendered = list.getCellRenderer().getListCellRendererComponent(
                        list, list.getModel().getElementAt(3), 3, false, false);
                List<JLabel> labels = findAll(rendered, JLabel.class);
                assertEquals(2, labels.size());
                assertEquals(expected, labels.get(1).getText());
                assertFalse(labels.get(1).getText().startsWith("<html>"));
                Rectangle bounds = list.getCellBounds(3, 3);
                rendered.setSize(bounds.getSize());
                layOut(rendered);
                for (JLabel label : labels) {
                    Rectangle labelBounds = SwingUtilities.convertRectangle(label.getParent(), label.getBounds(), rendered);
                    assertTrue(labelBounds.height >= label.getPreferredSize().height);
                    assertTrue(labelBounds.y + labelBounds.height <= bounds.height);
                }
            }
        });
    }

    @Test
    public void shouldFillViewportWithoutHorizontalScrollingWhenDetailsFit() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            DeclarationCheckReportPanel panel = new DeclarationCheckReportPanel(new DefaultListCellRenderer(),
                    DeclarationReportOutline.of(report(1, 1, 1)), Collections.emptyList());
            for (int width : Arrays.asList(800, 1000, 800)) {
                panel.setSize(width, 500);
                for (int pass = 0; pass < 3; pass++) {
                    layOut(panel);
                }
                JScrollPane scrollPane = findAll(panel, JScrollPane.class).get(0);
                assertFalse(scrollPane.getHorizontalScrollBar().isVisible());
                assertEquals(scrollPane.getViewport().getExtentSize().width, findAll(panel, MList.class).get(0).getWidth());
            }
        });
    }

    private static DeclarationCheckReportPanel.FindingRow findingRow(DeclarationReportOutline outline,
            java.util.Collection<OWLOntology> ontologies, int index) {
        return (DeclarationCheckReportPanel.FindingRow) DeclarationCheckReportPanel.rows(outline, ontologies).get(index);
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
