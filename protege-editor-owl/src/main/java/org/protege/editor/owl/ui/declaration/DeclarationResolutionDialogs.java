package org.protege.editor.owl.ui.declaration;

import org.protege.editor.owl.model.declaration.resolution.DeclarationResolutionPlanner;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLOntologyID;
import org.semanticweb.owlapi.model.OWLOntology;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Component;
import java.util.List;
import java.util.Optional;

/** Swing prompts for destination choice and explicit repair confirmation. */
final class DeclarationResolutionDialogs implements DeclarationResolutionActions.Interaction {

    private final Component parent;

    private final ListCellRenderer entityRenderer;

    DeclarationResolutionDialogs(Component parent, ListCellRenderer entityRenderer) {
        this.parent = parent;
        this.entityRenderer = entityRenderer;
    }

    @Override
    public OWLOntology chooseMissingTarget(OWLEntity entity, List<OWLOntology> ontologies,
                                           Optional<OWLOntologyID> suggested, int index, int total) {
        return chooseTarget("Add the missing declaration for", entity, ontologies, suggested, index, total);
    }

    @Override
    public OWLOntology chooseMisplacedTarget(OWLEntity entity, List<OWLOntology> ontologies,
                                             Optional<OWLOntologyID> suggested, int index, int total) {
        return chooseTarget("Move the misplaced declaration for", entity, ontologies, suggested, index, total);
    }

    private OWLOntology chooseTarget(String instruction, OWLEntity entity, List<OWLOntology> ontologies,
                                     Optional<OWLOntologyID> suggested, int index, int total) {
        DeclarationDestinationPanel choice = new DeclarationDestinationPanel(
                entityRenderer, entity, ontologies, suggested, instruction);
        while (JOptionPane.showConfirmDialog(parent, choice.getComponent(),
                "Choose declaration destination (" + index + " of " + total + ")",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.OK_OPTION) {
            if (choice.getSelectedOntology() != null) {
                return choice.getSelectedOntology();
            }
            JOptionPane.showMessageDialog(parent, "Choose an ontology for this entity before continuing.",
                    "Choose declaration destination", JOptionPane.INFORMATION_MESSAGE);
        }
        return null;
    }

    @Override
    public boolean confirm(DeclarationResolutionPlanner.Plan plan) {
        DeclarationResolutionPreview preview = new DeclarationResolutionPreview(plan, entityRenderer);
        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.add(new JLabel("Resolve " + plan.getFindingCount() + " findings with the following changes?"),
                BorderLayout.NORTH);
        content.add(preview.getComponent(), BorderLayout.CENTER);
        content.add(new JLabel("Changes are undoable. Save the affected ontologies to keep them."), BorderLayout.SOUTH);
        return JOptionPane.showConfirmDialog(parent, content, "Resolve entity declarations",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION;
    }

    @Override
    public void showError(String message) {
        JOptionPane.showMessageDialog(parent, message, DeclarationCheckReportPanel.TITLE, JOptionPane.ERROR_MESSAGE);
    }
}
