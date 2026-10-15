package org.protege.editor.owl.ui.declaration;

import org.protege.editor.owl.model.declaration.DeclarationChecker;
import org.protege.editor.owl.model.declaration.DeclarationReportOutline;
import org.protege.editor.owl.ui.action.ProtegeOWLAction;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyChangeListener;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Checks the active ontology and its import closure for missing and misplaced entity declarations
 * on demand, then shows the findings in a report dialog.
 *
 * <p>Unlike the save-time warning, this action always runs both checks: it does not consult the
 * Axioms preferences that would otherwise skip them. Checking and preparing report rows run off
 * the UI thread and change no preference or ontology. The report offers explicit, previewed
 * resolution actions; merely opening it applies none. Check failures are surfaced as errors.
 *
 * @author Josef Hardi
 */
public class CheckDeclarationsAction extends ProtegeOWLAction {

    private static final Logger logger = LoggerFactory.getLogger(CheckDeclarationsAction.class);

    private SwingWorker<List<Object>, Void> checkWorker;
    private boolean disposed;

    @Override
    public void actionPerformed(ActionEvent e) {
        if (disposed || (checkWorker != null && !checkWorker.isDone())) {
            return;
        }
        OWLOntology ontology = getOWLModelManager().getActiveOntology();
        AtomicBoolean changed = new AtomicBoolean();
        OWLOntologyChangeListener listener = changes -> changed.set(true);
        OWLOntologyManager manager = getOWLModelManager().getOWLOntologyManager();
        manager.addOntologyChangeListener(listener);
        setEnabled(false);
        checkWorker = new SwingWorker<List<Object>, Void>() {
            @Override
            protected List<Object> doInBackground() {
                return DeclarationCheckReportPanel.rows(DeclarationReportOutline.of(
                        DeclarationChecker.forcingAllChecks().check(ontology)), ontology.getImportsClosure());
            }

            @Override
            protected void done() {
                try {
                    if (disposed || isCancelled()) {
                        return;
                    }
                    List<Object> rows = get();
                    if (changed.get() || getOWLModelManager().getActiveOntology() != ontology) {
                        throw new IllegalStateException("The ontologies changed during the check. Run the check again.");
                    }
                    DeclarationCheckReportPanel.showDialog(getOWLEditorKit(), rows);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    showFailure(ex);
                } catch (ExecutionException ex) {
                    showFailure(ex.getCause());
                } catch (RuntimeException ex) {
                    showFailure(ex);
                } finally {
                    manager.removeOntologyChangeListener(listener);
                    if (!disposed) {
                        setEnabled(true);
                    }
                }
            }
        };
        checkWorker.execute();
    }

    private void showFailure(Throwable error) {
        logger.error("The entity declaration check failed", error);
        JOptionPane.showMessageDialog(getOWLWorkspace(),
                "The entity declaration check failed:\n" + error.getMessage(),
                DeclarationCheckReportPanel.TITLE, JOptionPane.ERROR_MESSAGE);
    }

    @Override
    public void initialise() throws Exception {
        // nothing to initialise
    }

    @Override
    public void dispose() {
        disposed = true;
        if (checkWorker != null) {
            checkWorker.cancel(false);
        }
    }
}
