package org.protege.editor.owl.ui.declaration;

import org.protege.editor.owl.model.declaration.DeclarationChecker;
import org.protege.editor.owl.model.declaration.DeclarationCheckStatus;
import org.protege.editor.owl.model.declaration.DeclarationReport;
import org.protege.editor.owl.model.declaration.DeclarationReportLogWriter;
import org.protege.editor.owl.model.declaration.DeclarationReportOutline;
import org.semanticweb.owlapi.model.OWLOntology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Coordinates declaration checking and user-facing reporting.
 *
 * @author Josef Hardi
 */
public final class DeclarationIssuesReporter {

    private static final Logger logger = LoggerFactory.getLogger(DeclarationIssuesReporter.class);

    @Nonnull
    private final Function<OWLOntology, DeclarationReport> checker;

    @Nonnull
    private final Consumer<DeclarationReportOutline> showWarningDialog;

    /**
     * Creates a reporter using the preference-driven declaration checker and the application log.
     *
     * @param parent the component over which to center declaration warnings, or {@code null} to
     *               use the default placement
     */
    public DeclarationIssuesReporter(@Nullable Component parent) {
        this(new DeclarationChecker()::check,
                outline -> DeclarationIssuesDialog.showDialog(parent, outline));
    }

    /**
     * Creates a reporter that uses the given checker and warning dialog.
     *
     * @param checker checks an ontology and its import closure
     * @param showWarningDialog shows a report when the checker finds at least one issue
     * @throws NullPointerException if either argument is {@code null}
     */
    DeclarationIssuesReporter(@Nonnull Function<OWLOntology, DeclarationReport> checker,
                              @Nonnull Consumer<DeclarationReportOutline> showWarningDialog) {
        this.checker = checkNotNull(checker);
        this.showWarningDialog = checkNotNull(showWarningDialog);
    }

    /**
     * Checks for any entity declaration issues in the import closure and reveals the findings in
     * a warning dialog.
     *
     * @param root the ontology whose import closure is checked
     * @throws NullPointerException if {@code root} is {@code null}
     */
    public void report(@Nonnull OWLOntology root) {
        checkNotNull(root);
        DeclarationReport report;
        try {
            report = checker.apply(root);
        } catch (RuntimeException e) {
            logger.error("The entity declaration check failed: {}", e.getMessage(), e);
            return;
        }
        if (isSwitchedOff(report)) {
            return;
        }
        DeclarationReportOutline outline = DeclarationReportOutline.of(report);
        DeclarationReportLogWriter.write(logger, root.getOntologyID(), outline);
        if (outline.hasFindings()) {
            showWarningDialog.accept(outline);
        }
    }

    private static boolean isSwitchedOff(@Nonnull DeclarationReport report) {
        return report.getMissing().getStatus() == DeclarationCheckStatus.SKIPPED
                && report.getMisplaced().getStatus() == DeclarationCheckStatus.SKIPPED;
    }
}
