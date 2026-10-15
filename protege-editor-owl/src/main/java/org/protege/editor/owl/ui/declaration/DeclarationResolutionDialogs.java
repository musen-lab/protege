package org.protege.editor.owl.ui.declaration;

import org.protege.editor.owl.model.declaration.DeclarationResolutionPlanner;
import org.protege.editor.owl.model.declaration.OntologyIdFormat;
import org.semanticweb.owlapi.model.OWLDeclarationAxiom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyChange;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.util.Comparator;
import java.util.List;

/** Swing prompts for destination choice and explicit repair confirmation. */
final class DeclarationResolutionDialogs implements DeclarationResolutionActions.Interaction {

    private final Component parent;

    DeclarationResolutionDialogs(Component parent) {
        this.parent = parent;
    }

    @Override
    public OWLOntology chooseMissingTarget(List<OWLOntology> ontologies, OWLOntology root) {
        JComboBox<OWLOntology> targets = new JComboBox<>();
        ontologies.stream().sorted(Comparator.comparing(ontology ->
                OntologyIdFormat.format(ontology.getOntologyID()))).forEach(targets::addItem);
        targets.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                           boolean selected, boolean focused) {
                String label = value instanceof OWLOntology
                        ? OntologyIdFormat.format(((OWLOntology) value).getOntologyID()) : "";
                return super.getListCellRendererComponent(list, label, index, selected, focused);
            }
        });
        if (ontologies.contains(root)) {
            targets.setSelectedItem(root);
        }
        JPanel choice = new JPanel(new BorderLayout(0, 8));
        choice.add(new JLabel("Add the selected missing declarations to:"), BorderLayout.NORTH);
        choice.add(targets, BorderLayout.CENTER);
        choice.add(new JLabel("Misplaced declarations will move to their suggested ontologies."), BorderLayout.SOUTH);
        return JOptionPane.showConfirmDialog(parent, choice, "Choose declaration destination",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.OK_OPTION
                ? (OWLOntology) targets.getSelectedItem() : null;
    }

    @Override
    public boolean confirm(DeclarationResolutionPlanner.Plan plan) {
        StringBuilder preview = new StringBuilder();
        for (OWLOntologyChange change : plan.getChanges()) {
            OWLDeclarationAxiom axiom = (OWLDeclarationAxiom) change.getAxiom();
            preview.append(change.isAddAxiom() ? "Add " : "Remove ")
                    .append(axiom.getEntity().getEntityType().getName()).append(" declaration: ")
                    .append(axiom.getEntity().getIRI()).append('\n')
                    .append("  ").append(change.isAddAxiom() ? "To: " : "From: ")
                    .append(OntologyIdFormat.format(change.getOntology().getOntologyID())).append('\n');
        }
        JTextArea detail = new JTextArea(preview.toString());
        detail.setEditable(false);
        detail.setCaretPosition(0);
        JScrollPane scroll = new JScrollPane(detail);
        scroll.setPreferredSize(new Dimension(650, 300));
        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.add(new JLabel("Resolve " + plan.getFindingCount() + " findings with the following changes?"),
                BorderLayout.NORTH);
        content.add(scroll, BorderLayout.CENTER);
        content.add(new JLabel("Changes are undoable. Save the affected ontologies to keep them."), BorderLayout.SOUTH);
        return JOptionPane.showConfirmDialog(parent, content, "Resolve entity declarations",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION;
    }

    @Override
    public void showError(String message) {
        JOptionPane.showMessageDialog(parent, message, DeclarationCheckReportPanel.TITLE, JOptionPane.ERROR_MESSAGE);
    }
}
