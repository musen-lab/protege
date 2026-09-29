package org.protege.editor.owl.ui.declaration;

import com.google.common.html.HtmlEscapers;
import org.protege.editor.core.ProtegeApplication;
import org.protege.editor.owl.model.declaration.DeclarationReportOutline;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.*;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Displays the post-save warning for entity declaration issues in an ontology's import closure.
 *
 * @author Josef Hardi
 */
public final class DeclarationIssuesDialog {

    static final String TITLE = "Save Ontology Warning";

    static final String SHOW_LOG = "Show log";

    static final String OK = "OK";

    private static final Object[] OPTIONS = {SHOW_LOG, OK};

    private static final int SHOW_LOG_OPTION = 0;

    private DeclarationIssuesDialog() {
    }

    /**
     * Shows the declaration-issue warning and opens the Protégé log if the user selects
     * {@value #SHOW_LOG}.
     *
     * @param parent the component over which to center the warning, or {@code null} to use the
     *               default placement
     * @param outline the declaration report outline summarized by the warning
     * @throws NullPointerException if {@code outline} is {@code null}
     */
    public static void showDialog(@Nullable Component parent, @Nonnull DeclarationReportOutline outline) {
        show(outline,
                message -> JOptionPane.showOptionDialog(parent, message, TITLE,
                        JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, OPTIONS, OK),
                ProtegeApplication::showLogView);
    }

    /**
     * Presents the warning through the supplied option chooser.
     *
     * @param outline the declaration report outline summarized by the warning
     * @param chooser the function that presents the message and returns the selected option index
     * @param showLog the action that opens the Protégé log
     * @throws NullPointerException if any argument is {@code null}
     */
    static void show(@Nonnull DeclarationReportOutline outline,
                     @Nonnull OptionChooser chooser,
                     @Nonnull Runnable showLog) {
        checkNotNull(outline);
        checkNotNull(chooser);
        checkNotNull(showLog);
        if (chooser.choose(warningMessageContentOf(outline)) == SHOW_LOG_OPTION) {
            showLog.run();
        }
    }

    /**
     * Builds the displayed warning text, as HTML, from the report outline.
     *
     * @param outline the declaration report outline.
     * @return a complete warning message.
     * @throws NullPointerException if {@code outline} is {@code null}
     */
    @Nonnull
    static String warningMessageContentOf(@Nonnull DeclarationReportOutline outline) {
        StringBuilder message = new StringBuilder(
                "<html>The ontology was saved with \"Preferences &gt; Axioms &gt; Suppress automatic entity "
                        + "declarations\" option enabled,<br>and the following issues remain:<br><br>");
        for (DeclarationReportOutline.Section section : outline.getSections()) {
            // Escaped, or HTML would drop "<detection disabled>" as an unknown tag.
            message.append("&nbsp;• ").append(HtmlEscapers.htmlEscaper().escape(section.getHeading()))
                    .append("<br>");
        }
        return message.append("<br>See the Protégé log for details.<br><br><b>Warning: Protégé may not load "
                + "ontologies with missing entity declarations correctly.</b></html>").toString();
    }

    interface OptionChooser {

        /**
         * Shows the supplied message and waits for the user to choose an option.
         *
         * @param message the warning text to present
         * @return the zero-based index of the selected option, or
         *         {@link JOptionPane#CLOSED_OPTION} if the warning was closed without a selection
         */
        int choose(@Nonnull String message);
    }
}
