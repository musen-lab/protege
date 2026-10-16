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

/** Tests the two-line suggestions independently of checking and log diagnostics. */
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
    public void shouldSuggestTheImportedFirstMentionRatherThanAllMentionersOrFindingMetadata() throws Exception {
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLOntology root = manager.createOntology(IRI.create("http://example.org/root"));
        OWLOntology module = manager.createOntology(IRI.create("http://example.org/module"));
        OWLDataFactory df = manager.getOWLDataFactory();
        manager.applyChange(new AddImport(root, df.getOWLImportsDeclaration(module.getOntologyID().getOntologyIRI().get())));
        OWLClass entity = df.getOWLClass(IRI.create("http://example.org/Undeclared"));
        manager.addAxiom(module, df.getOWLSubClassOfAxiom(entity, df.getOWLThing()));
        root.getSignature(org.semanticweb.owlapi.model.parameters.Imports.INCLUDED);
        MissingDeclarationFinding finding = MissingDeclarationFinding.get(entity,
                Arrays.asList(root.getOntologyID(), module.getOntologyID()));
        DeclarationReportOutline outline = missingOutline(Collections.singletonList(finding));
        assertEquals(Collections.singletonList("Suggested: Add to http://example.org/module"),
                findingRow(outline, root.getImportsClosure(), 1).getDetail());
        manager.addAxiom(root, df.getOWLSubClassOfAxiom(entity, df.getOWLThing()));
        assertEquals(Collections.singletonList("Suggested: Add to http://example.org/module"),
                findingRow(outline, root.getImportsClosure(), 1).getDetail());
        assertEquals(1, new MissingDeclarationChecker().check(root).size());
    }

    @Test
    public void shouldSuggestTheFirstMentionForAHeaderOnlyAnnotationProperty() throws Exception {
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = manager.getOWLDataFactory();
        OWLOntology root = manager.createOntology(IRI.create("http://example.org/root"));
        OWLOntology module = manager.createOntology(IRI.create("http://example.org/module"));
        manager.applyChange(new AddImport(root, df.getOWLImportsDeclaration(module.getOntologyID().getOntologyIRI().get())));
        OWLAnnotationProperty note = df.getOWLAnnotationProperty(IRI.create("http://example.org/headerNote"));
        manager.applyChange(new AddOntologyAnnotation(module, df.getOWLAnnotation(note, df.getOWLLiteral("module"))));
        DeclarationReportOutline outline = missingOutline(new MissingDeclarationChecker().check(root).getFindings());
        assertEquals(Collections.singletonList("Suggested: Add to http://example.org/module"),
                findingRow(outline, root.getImportsClosure(), 1).getDetail());
        manager.applyChange(new AddOntologyAnnotation(root, df.getOWLAnnotation(note, df.getOWLLiteral("root"))));
        assertEquals(Collections.singletonList("Suggested: Add to http://example.org/module"),
                findingRow(outline, root.getImportsClosure(), 1).getDetail());
    }

    @Test
    public void shouldNotInventATargetForSiblingOrCyclicFirstMentions() throws Exception {
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = manager.getOWLDataFactory();
        OWLOntology root = manager.createOntology(IRI.create("http://example.org/root"));
        OWLOntology left = manager.createOntology(IRI.create("http://example.org/left"));
        OWLOntology right = manager.createOntology(IRI.create("http://example.org/right"));
        for (OWLOntology member : Arrays.asList(left, right)) {
            manager.applyChange(new AddImport(root, df.getOWLImportsDeclaration(member.getOntologyID().getOntologyIRI().get())));
        }
        OWLClass entity = df.getOWLClass(IRI.create("urn:ambiguous"));
        manager.addAxiom(left, df.getOWLSubClassOfAxiom(entity, df.getOWLThing()));
        manager.addAxiom(right, df.getOWLSubClassOfAxiom(entity, df.getOWLThing()));
        DeclarationReportOutline outline = missingOutline(new MissingDeclarationChecker().check(root).getFindings());
        String expected = "Suggested: Add to (no unique first mention)";
        assertEquals(Collections.singletonList(expected), findingRow(outline, root.getImportsClosure(), 1).getDetail());
        manager.applyChange(new AddImport(left, df.getOWLImportsDeclaration(right.getOntologyID().getOntologyIRI().get())));
        manager.applyChange(new AddImport(right, df.getOWLImportsDeclaration(left.getOntologyID().getOntologyIRI().get())));
        assertEquals(Collections.singletonList(expected), findingRow(outline, root.getImportsClosure(), 1).getDetail());
    }

    @Test(timeout = 30_000)
    public void shouldSuggestEveryMissingEntityInALargeClosureWithoutRescanningPerFinding() throws Exception {
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = manager.getOWLDataFactory();
        OWLOntology root = manager.createOntology(IRI.create("http://example.org/scale/root"));
        List<OWLOntology> members = new ArrayList<>();
        for (int o = 0; o < 20; o++) {
            IRI memberIri = IRI.create("http://example.org/scale/o" + o);
            members.add(manager.createOntology(memberIri));
            manager.applyChange(new AddImport(root, df.getOWLImportsDeclaration(memberIri)));
        }
        List<MissingDeclarationFinding> findings = new ArrayList<>();
        for (int i = 0; i < 100_000; i++) {
            OWLClass term = df.getOWLClass(IRI.create("http://example.org/scale#C" + i));
            OWLOntology member = members.get(i % members.size());
            if (i % 10 == 0) {
                manager.addAxiom(member, df.getOWLSubClassOfAxiom(term, df.getOWLThing()));
                findings.add(MissingDeclarationFinding.get(term, Collections.singletonList(root.getOntologyID())));
            } else {
                manager.addAxiom(member, df.getOWLDeclarationAxiom(term));
            }
        }
        List<Object> rows = DeclarationCheckReportPanel.rows(missingOutline(findings), root.getImportsClosure());
        assertEquals(findings.size() + 3, rows.size());
        for (int index = 1; index <= findings.size(); index++) {
            DeclarationCheckReportPanel.FindingRow row = (DeclarationCheckReportPanel.FindingRow) rows.get(index);
            String iri = row.getEntity().getIRI().toString();
            int term = Integer.parseInt(iri.substring(iri.lastIndexOf("#C") + 2));
            assertEquals(Collections.singletonList("Suggested: Add to http://example.org/scale/o" + term % members.size()),
                    row.getDetail());
        }
    }

    @Test
    public void shouldRenderTwoSpacedLinesWithCompactRuleNames() throws Exception {
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
                assertEquals(index == 4
                        ? "Suggested: Move to http://purl.obolibrary.org/obo/go.owl (based on: OBO ID)"
                        : "Suggested: Add to http://example.org/pizza", labels.get(1).getText());
                JComponent content = (JComponent) ((Container) rendered).getComponent(0);
                assertEquals(2, content.getInsets().top);
                assertEquals(2, content.getInsets().bottom);
                assertTrue(((JComponent) rendered).getInsets().left <= 1); // Only MList's transparent item inset.
            }
        });
    }

    @Test
    public void shouldKeepLongSuggestedTargetsAndUnknownRuleNamesOnOneScrollableLine() throws Exception {
        String longIri = "http://example.org/" + String.join("", Collections.nCopies(100, "long-module-name"));
        MisplacedDeclarationFinding finding = MisplacedDeclarationFinding.get(
                OWLManager.getOWLDataFactory().getOWLClass(IRI.create("http://example.org/QuickBaking")),
                new OWLOntologyID(IRI.create(longIri)),
                Collections.singletonList(new OWLOntologyID(IRI.create("http://example.org/menu"))), "test-rule");
        DeclarationReportOutline outline = DeclarationReportOutline.of(DeclarationReport.get(
                MissingDeclarationReport.get(Collections.emptyList()),
                MisplacedDeclarationReport.get(Collections.singletonList(finding))));
        String expected = "Suggested: Move to " + longIri + " (based on: test-rule)";
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
                JScrollPane scroll = findAll(panel, JScrollPane.class).get(0);
                assertTrue(scroll.getHorizontalScrollBar().isVisible());
                assertTrue(list.getPreferredSize().width > scroll.getViewport().getExtentSize().width);
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
                JScrollPane scroll = findAll(panel, JScrollPane.class).get(0);
                assertFalse(scroll.getHorizontalScrollBar().isVisible());
                assertEquals(scroll.getViewport().getExtentSize().width, findAll(panel, MList.class).get(0).getWidth());
            }
        });
    }

    @Test
    public void shouldRestoreSelectionColorsWhenTheSharedRendererIsReused() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            DeclarationCheckReportPanel panel = new DeclarationCheckReportPanel(new DefaultListCellRenderer(),
                    DeclarationReportOutline.of(report(1, 1, 1)), Collections.emptyList());
            MList list = findAll(panel, MList.class).get(0);
            Color foreground = new Color(240, 245, 250);
            Color background = new Color(30, 60, 90);
            list.setSelectionForeground(foreground);
            list.setSelectionBackground(background);
            Color secondary = UIManager.getColor("Label.disabledForeground");
            for (int index : Arrays.asList(1, 2, 4, 1)) {
                for (boolean selected : Arrays.asList(false, true, false, true)) {
                    Component rendered = list.getCellRenderer().getListCellRendererComponent(
                            list, list.getModel().getElementAt(index), index, selected, false);
                    List<JLabel> labels = findAll(rendered, JLabel.class);
                    assertEquals(2, labels.size());
                    assertEquals(selected ? foreground : secondary != null ? secondary : Color.GRAY, labels.get(1).getForeground());
                    assertEquals(selected ? background : list.getBackground(), rendered.getBackground());
                    assertTrue(labels.get(1).getText().startsWith(index == 4 ? "Suggested: Move to " : "Suggested: Add to "));
                }
            }
        });
    }

    @Test
    public void shouldPaintSelectionToTheLeftEdgeWithoutClippingEitherLine() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            DeclarationCheckReportPanel panel = new DeclarationCheckReportPanel(new DefaultListCellRenderer(),
                    DeclarationReportOutline.of(report(2, 0, 0)), Collections.emptyList());
            MList list = findAll(panel, MList.class).get(0);
            Color selected = new Color(0, 70, 220);
            list.setSelectionBackground(selected);
            JComponent rendered = (JComponent) list.getCellRenderer().getListCellRendererComponent(
                    list, list.getModel().getElementAt(1), 1, true, false);
            rendered.setSize(700, rendered.getPreferredSize().height);
            layOut(rendered);
            java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(
                    rendered.getWidth(), rendered.getHeight(), java.awt.image.BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            try {
                rendered.paint(graphics);
            } finally {
                graphics.dispose();
            }
            assertEquals(selected.getRGB(), image.getRGB(0, rendered.getHeight() / 2));
            for (JLabel label : findAll(rendered, JLabel.class)) {
                Rectangle bounds = SwingUtilities.convertRectangle(label.getParent(), label.getBounds(), rendered);
                assertTrue(bounds.height >= label.getPreferredSize().height);
                assertTrue(bounds.y >= 0);
                assertTrue(bounds.y + bounds.height <= rendered.getHeight());
            }
        });
    }

    private static DeclarationReportOutline missingOutline(List<MissingDeclarationFinding> findings) {
        return DeclarationReportOutline.of(DeclarationReport.get(MissingDeclarationReport.get(findings),
                MisplacedDeclarationReport.get(Collections.emptyList())));
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
