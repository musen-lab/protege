package org.protege.editor.owl.ui.declaration;

import org.protege.editor.owl.model.OWLModelManager;
import org.protege.editor.owl.model.declaration.*;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyChangeListener;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Coordinates selection, preview, model changes and report refresh; it does not plan repairs. */
final class DeclarationResolutionActions {

    private static final Logger logger = LoggerFactory.getLogger(DeclarationResolutionActions.class);

    interface Interaction {
        OWLOntology chooseMissingTarget(List<OWLOntology> ontologies, OWLOntology root);
        boolean confirm(DeclarationResolutionPlanner.Plan plan);
        void showError(String message);
    }

    private final OWLModelManager modelManager;
    private final OWLOntologyManager ontologyManager;
    private final OWLOntology root;
    private final JList<?> list;
    private final Consumer<List<Object>> display;
    private final Interaction interaction;
    private final JButton selected = new JButton("Resolve selected…");
    private final JButton all = new JButton("Resolve all…");
    private final JButton refresh = new JButton("Refresh");
    private final JLabel progress = new JLabel();
    private final JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEADING));
    private final AtomicLong revision = new AtomicLong();
    private final OWLOntologyChangeListener changeListener = changes -> revision.incrementAndGet();
    private boolean hasFindings;
    private boolean disposed;
    private SwingWorker<?, ?> worker;

    DeclarationResolutionActions(OWLModelManager modelManager, OWLOntology root, JList<?> list,
                                 Consumer<List<Object>> display, Interaction interaction) {
        this.modelManager = modelManager;
        this.ontologyManager = modelManager.getOWLOntologyManager();
        this.root = root;
        this.list = list;
        this.display = display;
        this.interaction = interaction;
        ontologyManager.addOntologyChangeListener(changeListener);
        selected.setToolTipText("Resolve the selected findings; use Ctrl/Command or Shift to select multiple rows.");
        all.setToolTipText("Preview repairs for all findings in this report.");
        selected.addActionListener(event -> resolve(false));
        all.addActionListener(event -> resolve(true));
        refresh.addActionListener(event -> refresh());
        toolbar.add(selected);
        toolbar.add(all);
        toolbar.add(refresh);
        toolbar.add(progress);
        list.addListSelectionListener(event -> updateEnabled());
        hasFindings = !findings(true).isEmpty();
        updateEnabled();
    }

    JComponent getToolbar() {
        return toolbar;
    }

    boolean isBusy() {
        return worker != null;
    }

    void dispose() {
        disposed = true;
        ontologyManager.removeOntologyChangeListener(changeListener);
        if (worker != null) {
            worker.cancel(false);
        }
    }

    private List<Object> findings(boolean everyRow) {
        List<?> values;
        if (everyRow) {
            List<Object> rows = new ArrayList<>();
            for (int i = 0; i < list.getModel().getSize(); i++) {
                rows.add(list.getModel().getElementAt(i));
            }
            values = rows;
        } else {
            values = list.getSelectedValuesList();
        }
        List<Object> findings = new ArrayList<>();
        for (Object value : values) {
            if (value instanceof DeclarationCheckReportPanel.FindingRow) {
                findings.add(((DeclarationCheckReportPanel.FindingRow) value).getFinding());
            }
        }
        return findings;
    }

    private void updateEnabled() {
        boolean selectedFinding = false;
        // No full-list scan on selection changes, even when the report has thousands of rows.
        for (int index : list.getSelectedIndices()) {
            if (list.getModel().getElementAt(index) instanceof DeclarationCheckReportPanel.FindingRow) {
                selectedFinding = true;
                break;
            }
        }
        selected.setEnabled(!disposed && !isBusy() && selectedFinding);
        all.setEnabled(!disposed && !isBusy() && hasFindings);
        refresh.setEnabled(!disposed && !isBusy());
    }

    private void requireCurrentRoot() {
        if (disposed || modelManager.getActiveOntology() != root
                || !ontologyManager.getOntologies().contains(root)) {
            throw new IllegalStateException("The active ontology has changed or was closed. "
                    + "Close this report and run Check entity declarations again.");
        }
    }

    private void requireRevision(long expected) {
        requireCurrentRoot();
        if (revision.get() != expected) {
            throw new IllegalStateException("The ontologies changed while preparing repairs. Refresh the report and try again.");
        }
    }

    private void resolve(boolean everyRow) {
        if (isBusy() || disposed) {
            return;
        }
        try {
            requireCurrentRoot();
            List<MissingDeclarationFinding> missing = new ArrayList<>();
            List<MisplacedDeclarationFinding> misplaced = new ArrayList<>();
            for (Object finding : findings(everyRow)) {
                if (finding instanceof MissingDeclarationFinding) {
                    missing.add((MissingDeclarationFinding) finding);
                } else if (finding instanceof MisplacedDeclarationFinding) {
                    misplaced.add((MisplacedDeclarationFinding) finding);
                }
            }
            if (missing.isEmpty() && misplaced.isEmpty()) {
                return;
            }
            Set<OWLOntology> editable = new HashSet<>();
            root.getImportsClosure().stream().filter(modelManager::isMutable).forEach(editable::add);
            OWLOntology target = chooseTarget(missing, editable);
            if (!missing.isEmpty() && target == null) {
                return;
            }
            requireCurrentRoot();
            long expected = revision.get();
            DeclarationResolutionPlanner planner = new DeclarationResolutionPlanner(root, editable::contains);
            runInBackground(() -> planner.plan(missing, misplaced, target),
                    plan -> confirmAndApply(plan, expected), "Preparing repairs…");
        } catch (RuntimeException ex) {
            reportError(ex, "No repairs were applied.\n");
        }
    }

    private OWLOntology chooseTarget(List<MissingDeclarationFinding> missing, Set<OWLOntology> editable) {
        if (missing.isEmpty()) {
            return null;
        }
        if (editable.isEmpty()) {
            throw new IllegalStateException("No ontology in this report's import closure is editable.");
        }
        return interaction.chooseMissingTarget(Collections.unmodifiableList(new ArrayList<>(editable)), root);
    }

    private void confirmAndApply(DeclarationResolutionPlanner.Plan plan, long expected) {
        requireRevision(expected);
        if (!interaction.confirm(plan)) {
            return;
        }
        // A revision counter catches changes during the modal preview without another closure scan.
        requireRevision(expected);
        Set<OWLOntology> affected = new HashSet<>();
        plan.getChanges().forEach(change -> affected.add(change.getOntology()));
        for (OWLOntology ontology : affected) {
            if (!modelManager.isMutable(ontology)) {
                throw new IllegalStateException("An affected ontology is no longer editable. Refresh the report.");
            }
        }
        try {
            // Keep application on the EDT: Protege's history and view listeners expect UI edits here.
            modelManager.applyChanges(plan.getChanges());
        } catch (RuntimeException ex) {
            reportError(ex, "Some edits may have been applied. Check the ontology history and refresh the report.\n");
            return;
        }
        refreshReport();
    }

    private void refresh() {
        if (isBusy() || disposed) {
            return;
        }
        try {
            requireCurrentRoot();
            refreshReport();
        } catch (RuntimeException ex) {
            reportError(ex, "Could not refresh the report.\n");
        }
    }

    private void refreshReport() {
        long expected = revision.get();
        runInBackground(() -> DeclarationCheckReportPanel.rows(
                        DeclarationReportOutline.of(DeclarationChecker.forcingAllChecks().check(root)),
                        root.getImportsClosure()),
                rows -> {
                    requireRevision(expected);
                    display.accept(rows);
                    hasFindings = rows.stream().anyMatch(DeclarationCheckReportPanel.FindingRow.class::isInstance);
                    list.clearSelection();
                }, "Refreshing report…");
    }

    /** Runs expensive ontology reads off the EDT; callbacks and all Swing operations stay on it. */
    private <T> void runInBackground(Supplier<T> operation, Consumer<T> completed, String message) {
        SwingWorker<T, Void> next = new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() {
                return operation.get();
            }

            @Override
            protected void done() {
                try {
                    if (!disposed && !isCancelled()) {
                        completed.accept(get());
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    reportError(ex, "The operation was interrupted. Refresh the report before retrying.\n");
                } catch (ExecutionException ex) {
                    reportError(ex.getCause(), "Could not prepare or refresh the report. No new repairs were applied.\n");
                } catch (RuntimeException ex) {
                    reportError(ex, "Could not complete the operation. Refresh the report before retrying.\n");
                } finally {
                    if (worker == this) {
                        worker = null;
                        progress.setText("");
                    }
                    updateEnabled();
                }
            }
        };
        worker = next;
        progress.setText(message);
        updateEnabled();
        next.execute();
    }

    private void reportError(Throwable error, String context) {
        if (disposed) {
            return;
        }
        logger.error("Entity declaration resolution failed", error);
        interaction.showError(context + error.getMessage());
    }
}
