package org.protege.editor.owl.ui.declaration;

import org.junit.Test;
import org.protege.editor.owl.OWLEditorKit;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;

import javax.swing.*;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.*;
import static org.protege.editor.owl.ui.renderer.RenderingEscapeUtils.RenderingEscapeSetting.UNESCAPED_RENDERING;
import static org.mockito.Mockito.*;

public class DeclarationDestinationPanel_TestCase {

    @Test
    public void shouldRenderTheEntityLabelAndIconAndPreselectItsSuggestedOntology() throws Exception {
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLOntology root = manager.createOntology(IRI.create("http://example.org/root"));
        OWLOntology suggested = manager.createOntology(IRI.create("http://example.org/module"));
        OWLClass entity = manager.getOWLDataFactory().getOWLClass(IRI.create("http://example.org/Customer"));
        Icon icon = new ImageIcon(new java.awt.image.BufferedImage(12, 12, java.awt.image.BufferedImage.TYPE_INT_ARGB));
        SwingUtilities.invokeAndWait(() -> {
            ListCellRenderer renderer = (list, value, index, selected, focused) -> {
                assertEquals(entity, value);
                return new JLabel("Customer", icon, SwingConstants.LEADING);
            };
            DeclarationDestinationPanel panel = new DeclarationDestinationPanel(renderer, entity,
                    Arrays.asList(root, suggested), Optional.of(suggested.getOntologyID()));
            assertEquals(suggested, panel.getSelectedOntology());
            List<JLabel> labels = find(panel.getComponent(), JLabel.class);
            assertTrue(labels.stream().anyMatch(label -> label.getText().equals("Add the missing declaration for")));
            assertTrue(labels.stream().anyMatch(label -> label.getText().equals("Customer") && label.getIcon() == icon));
            assertTrue(labels.stream().anyMatch(label -> label.getText().equals("to:")));
            JLabel prefix = labels.stream().filter(label -> label.getText().equals("Suggested location: ")).findFirst().get();
            JLabel ontologyName = labels.stream().filter(label -> label.getText().equals("http://example.org/module")).findFirst().get();
            assertFalse(prefix.getFont().isBold());
            assertTrue(ontologyName.getFont().isBold());
            assertTrue(prefix.getFont().getSize2D() < new JLabel().getFont().getSize2D());
            assertEquals(prefix.getFont().getSize2D(), ontologyName.getFont().getSize2D(), 0.0f);
            java.awt.Color secondary = UIManager.getColor("Label.disabledForeground");
            assertEquals(secondary != null ? secondary : java.awt.Color.GRAY, ontologyName.getForeground());
            find(panel.getComponent(), JComboBox.class).get(0).setSelectedItem(root);
            assertEquals(root, panel.getSelectedOntology());
        });
    }

    @Test
    public void shouldLeaveAnAmbiguousSuggestionUnselectedRatherThanDefaultingToTheFirstOntology() throws Exception {
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLOntology root = manager.createOntology();
        OWLClass entity = manager.getOWLDataFactory().getOWLClass(IRI.create("urn:entity"));
        SwingUtilities.invokeAndWait(() -> {
            DeclarationDestinationPanel panel = new DeclarationDestinationPanel(
                    new DefaultListCellRenderer(), entity, Collections.singletonList(root), Optional.empty());
            assertNull(panel.getSelectedOntology());
            assertTrue(find(panel.getComponent(), JLabel.class).stream().anyMatch(label ->
                    label.getText().equals("No unique first mention")));
        });
    }

    @Test
    public void shouldShowANoneditableSuggestionWithoutSelectingAnUnrelatedOntology() throws Exception {
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLOntology root = manager.createOntology();
        OWLOntologyID suggested = new OWLOntologyID(IRI.create("http://example.org/read-only"));
        OWLClass entity = manager.getOWLDataFactory().getOWLClass(IRI.create("urn:entity"));
        SwingUtilities.invokeAndWait(() -> {
            DeclarationDestinationPanel panel = new DeclarationDestinationPanel(
                    new DefaultListCellRenderer(), entity, Collections.singletonList(root), Optional.of(suggested));
            assertNull(panel.getSelectedOntology());
            assertTrue(find(panel.getComponent(), JLabel.class).stream().anyMatch(label ->
                    label.getText().equals("http://example.org/read-only")));
            assertTrue(find(panel.getComponent(), JLabel.class).stream().anyMatch(label ->
                    label.getText().equals(" (not editable)")));
        });
    }

    @Test
    public void shouldRenderTheFullEntityWithoutExtraTrailingSpace() throws Exception {
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLOntology ontology = manager.createOntology();
        OWLClass entity = manager.getOWLDataFactory().getOWLClass(IRI.create("urn:LongEntityName"));
        SwingUtilities.invokeAndWait(() -> {
            OWLEditorKit editorKit = mock(OWLEditorKit.class, RETURNS_DEEP_STUBS);
            Icon icon = new ImageIcon(new java.awt.image.BufferedImage(24, 24,
                    java.awt.image.BufferedImage.TYPE_INT_ARGB));
            String name = "Long entity name that must remain fully visible";
            when(editorKit.getOWLModelManager().getDisabmiguatedRendering(entity, UNESCAPED_RENDERING)).thenReturn(name);
            when(editorKit.getWorkspace().getOWLIconProvider().getIcon(entity)).thenReturn(icon);
            DeclarationDestinationPanel panel = new DeclarationDestinationPanel(
                    DeclarationDestinationPanel.entityRenderer(editorKit), entity,
                    Collections.singletonList(ontology), Optional.of(ontology.getOntologyID()));
            JLabel rendered = find(panel.getComponent(), JLabel.class).stream()
                    .filter(label -> name.equals(label.getText())).findFirst().get();
            assertSame(icon, rendered.getIcon());
            JComponent content = panel.getComponent();
            content.setSize(content.getPreferredSize());
            layOut(content);
            assertTrue(rendered.getWidth() >= rendered.getPreferredSize().width);
            assertTrue(rendered.getHeight() >= icon.getIconHeight());
            assertTrue(rendered.getX() + rendered.getWidth() <= rendered.getParent().getWidth());
            JLabel suffix = find(panel.getComponent(), JLabel.class).stream()
                    .filter(label -> "to:".equals(label.getText())).findFirst().get();
            assertEquals(3, suffix.getX() - rendered.getX() - rendered.getWidth());
            java.awt.Rectangle view = new java.awt.Rectangle(0, 0, rendered.getWidth(), rendered.getHeight());
            java.awt.Rectangle iconBounds = new java.awt.Rectangle();
            java.awt.Rectangle textBounds = new java.awt.Rectangle();
            String displayed = SwingUtilities.layoutCompoundLabel(rendered, rendered.getFontMetrics(rendered.getFont()),
                    name, icon, rendered.getVerticalAlignment(), rendered.getHorizontalAlignment(),
                    rendered.getVerticalTextPosition(), rendered.getHorizontalTextPosition(),
                    view, iconBounds, textBounds, rendered.getIconTextGap());
            assertEquals(name, displayed);
            assertTrue(rendered.getWidth() - textBounds.x - textBounds.width <= 1);
        });
    }

    @Test
    public void shouldUseTheSameCompactHintLayoutForMisplacedDeclarations() throws Exception {
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLOntology owner = manager.createOntology(IRI.create("http://example.org/owner"));
        OWLClass entity = manager.getOWLDataFactory().getOWLClass(IRI.create("urn:entity"));
        SwingUtilities.invokeAndWait(() -> {
            DeclarationDestinationPanel panel = new DeclarationDestinationPanel(new DefaultListCellRenderer(),
                    entity, Collections.singletonList(owner), Optional.of(owner.getOntologyID()),
                    "Move the misplaced declaration for");
            assertEquals(owner, panel.getSelectedOntology());
            JComponent content = panel.getComponent();
            content.setSize(content.getPreferredSize());
            layOut(content);
            List<JLabel> labels = find(content, JLabel.class);
            assertTrue(labels.stream().anyMatch(label -> "Move the misplaced declaration for".equals(label.getText())));
            JLabel hint = labels.stream().filter(label -> "Suggested location: ".equals(label.getText())).findFirst().get();
            JLabel name = labels.stream().filter(label -> "http://example.org/owner".equals(label.getText())).findFirst().get();
            assertTrue(name.getFont().isBold());
            JComboBox dropdown = find(content, JComboBox.class).get(0);
            Container hintPanel = hint.getParent();
            assertSame(dropdown.getParent(), hintPanel.getParent());
            assertEquals(2, hintPanel.getY() - dropdown.getY() - dropdown.getHeight());
        });
    }

    private static void layOut(Container component) {
        component.doLayout();
        for (Component child : component.getComponents()) {
            if (child instanceof Container) {
                layOut((Container) child);
            }
        }
    }

    private static <T> List<T> find(Component component, Class<T> type) {
        List<T> matches = new ArrayList<>();
        if (type.isInstance(component)) {
            matches.add(type.cast(component));
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                matches.addAll(find(child, type));
            }
        }
        return matches;
    }
}
