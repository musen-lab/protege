package org.protege.editor.owl.ui.declaration;

import org.protege.editor.owl.model.declaration.DeclarationChecker;
import org.protege.editor.owl.model.declaration.DeclarationReport;
import org.protege.editor.owl.model.declaration.DeclarationReportOutline;
import org.protege.editor.owl.ui.action.ProtegeOWLAction;
import org.semanticweb.owlapi.model.OWLOntology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.event.ActionEvent;

/**
 * Checks the active ontology and its import closure for missing and misplaced entity declarations
 * on demand, then shows the findings in a report dialog.
 *
 * <p>Unlike the save-time warning, this action always runs both checks: it does not consult the
 * Axioms preferences that would otherwise skip them. The check is read-only - it changes no
 * preference and modifies no ontology - and a check failure is surfaced as an error rather than
 * shown as a clean report.
 *
 * @author Josef Hardi
 */
public class CheckDeclarationsAction extends ProtegeOWLAction {

    private static final Logger logger = LoggerFactory.getLogger(CheckDeclarationsAction.class);

    @Override
    public void actionPerformed(ActionEvent e) {
        OWLOntology ontology = getOWLModelManager().getActiveOntology();
        DeclarationReport report;
        try {
            report = DeclarationChecker.forcingAllChecks().check(ontology);
        } catch (RuntimeException ex) {
            logger.error("The entity declaration check failed: {}", ex.getMessage(), ex);
            JOptionPane.showMessageDialog(getOWLWorkspace(),
                    "The entity declaration check failed:\n" + ex.getMessage(),
                    DeclarationCheckReportPanel.TITLE, JOptionPane.ERROR_MESSAGE);
            return;
        }
        DeclarationCheckReportPanel.showDialog(getOWLEditorKit(), DeclarationReportOutline.of(report));
    }

    @Override
    public void initialise() throws Exception {
        // nothing to initialise
    }

    @Override
    public void dispose() {
        // nothing to dispose
    }
}
