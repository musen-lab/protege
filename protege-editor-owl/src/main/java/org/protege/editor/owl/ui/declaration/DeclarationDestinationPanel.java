package org.protege.editor.owl.ui.declaration;

import org.protege.editor.owl.model.declaration.OntologyIdFormat;
import org.protege.editor.owl.OWLEditorKit;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyID;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Color;
import java.util.List;
import java.util.Optional;

import static org.protege.editor.owl.ui.renderer.RenderingEscapeUtils.RenderingEscapeSetting.UNESCAPED_RENDERING;

/** Composes the entity renderer, destination chooser and first-mention suggestion for one entity. */
final class DeclarationDestinationPanel {

    private final JPanel component = new JPanel(new BorderLayout(0, 8));
    private final JComboBox<OWLOntology> targets = new JComboBox<>();

    DeclarationDestinationPanel(ListCellRenderer entityRenderer, OWLEntity entity,
                                 List<OWLOntology> ontologies, Optional<OWLOntologyID> suggested) {
        this(entityRenderer, entity, ontologies, suggested, "Add the missing declaration for");
    }

    DeclarationDestinationPanel(ListCellRenderer entityRenderer, OWLEntity entity,
                                 List<OWLOntology> ontologies, Optional<OWLOntologyID> suggested,
                                 String instruction) {
        JPanel heading = new JPanel(new FlowLayout(FlowLayout.LEADING, 3, 0));
        heading.add(new JLabel(instruction));
        heading.add(entityRenderer.getListCellRendererComponent(new JList<>(), entity, -1, false, false));
        heading.add(new JLabel("to:"));
        component.add(heading, BorderLayout.NORTH);
        ontologies.forEach(targets::addItem);
        targets.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                           boolean selected, boolean focused) {
                String label = value instanceof OWLOntology
                        ? OntologyIdFormat.format(((OWLOntology) value).getOntologyID()) : "Choose an ontology…";
                return super.getListCellRendererComponent(list, label, index, selected, focused);
            }
        });
        targets.setSelectedIndex(-1);
        for (OWLOntology ontology : ontologies) {
            if (suggested.filter(ontology.getOntologyID()::equals).isPresent()) {
                targets.setSelectedItem(ontology);
                break;
            }
        }
        JPanel destination = new JPanel(new BorderLayout(0, 2));
        destination.add(targets, BorderLayout.NORTH);
        component.add(destination, BorderLayout.CENTER);
        JPanel hint = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
        hint.add(suggestionLabel("Suggested location: ", false));
        hint.add(suggestionLabel(suggested.map(OntologyIdFormat::format).orElse("No unique first mention"),
                suggested.isPresent()));
        if (suggested.isPresent() && targets.getSelectedItem() == null) {
            hint.add(suggestionLabel(" (not editable)", false));
        }
        destination.add(hint, BorderLayout.CENTER);
    }

    /** A standalone label avoids the list-cell renderer's extra trailing width. */
    static ListCellRenderer<OWLEntity> entityRenderer(OWLEditorKit editorKit) {
        return (list, entity, index, selected, focused) -> new JLabel(
                editorKit.getOWLModelManager().getDisabmiguatedRendering(entity, UNESCAPED_RENDERING),
                editorKit.getWorkspace().getOWLIconProvider().getIcon(entity), SwingConstants.LEADING);
    }

    private static JLabel suggestionLabel(String text, boolean bold) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(bold ? Font.BOLD : Font.PLAIN,
                Math.max(10.0f, label.getFont().getSize2D() - 2.0f)));
        Color secondary = UIManager.getColor("Label.disabledForeground");
        label.setForeground(secondary != null ? secondary : Color.GRAY);
        return label;
    }

    JComponent getComponent() {
        return component;
    }

    OWLOntology getSelectedOntology() {
        return (OWLOntology) targets.getSelectedItem();
    }
}
