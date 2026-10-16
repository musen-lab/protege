package org.protege.editor.owl.ui.declaration;

import org.protege.editor.owl.model.declaration.DeclarationChecker;
import org.protege.editor.owl.model.declaration.DeclarationReport;
import org.protege.editor.owl.model.declaration.DeclarationReportOutline;
import org.protege.editor.owl.model.event.OWLModelManagerListener;
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
 * the UI thread and change no preference or ontology. The report offers resolution actions and
 * check failures are surfaced as errors.
 *
 * @author Josef Hardi
 */
public class CheckDeclarationsAction extends ProtegeOWLAction {

    private static final Logger logger = LoggerFactory.getLogger(CheckDeclarationsAction.class);

    private SwingWorker<List<Object>, Void> checkWorker;
    private boolean disposed;
    private final OWLModelManagerListener modelListener = event -> requestEnabledUpdate();
    private final OWLOntologyChangeListener contentListener = changes -> requestEnabledUpdate();

    @Override
    public void actionPerformed(ActionEvent e) {
        if (disposed || checkWorker != null || !hasCheckableOntology()) {
            updateEnabledState();
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
                DeclarationReport report = DeclarationChecker.forcingAllChecks().check(ontology);
                return DeclarationCheckReportPanel.rows(DeclarationReportOutline.of(report));
            }

            @Override
            protected void done() {
                try {
                    if (disposed || isCancelled()) {
                        return;
                    }
                    List<Object> rows = get();
                    if (!hasCheckableOntology()) {
                        return;
                    }
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
                    checkWorker = null;
                    updateEnabledState();
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

    private boolean hasCheckableOntology() {
        OWLOntology ontology = getOWLModelManager().getActiveOntology();
        return ontology != null && getOWLModelManager().getOntologies().contains(ontology) && !ontology.isEmpty();
    }

    private void requestEnabledUpdate() {
        if (SwingUtilities.isEventDispatchThread()) {
            updateEnabledState();
        } else {
            SwingUtilities.invokeLater(this::updateEnabledState);
        }
    }

    private void updateEnabledState() {
        setEnabled(!disposed && checkWorker == null && hasCheckableOntology());
    }

    @Override
    public void initialise() throws Exception {
        getOWLModelManager().addListener(modelListener);
        getOWLModelManager().addOntologyChangeListener(contentListener);
        updateEnabledState();
    }

    @Override
    public void dispose() {
        disposed = true;
        getOWLModelManager().removeListener(modelListener);
        getOWLModelManager().removeOntologyChangeListener(contentListener);
        setEnabled(false);
        if (checkWorker != null) {
            checkWorker.cancel(false);
        }
    }
}
