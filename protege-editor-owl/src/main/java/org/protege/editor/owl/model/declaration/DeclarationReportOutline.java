package org.protege.editor.owl.model.declaration;

import com.google.common.collect.ImmutableList;

import javax.annotation.Nonnull;
import java.util.Locale;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Provides the presentation structure shared by declaration report renderers.
 *
 * @author Josef Hardi
 */
public final class DeclarationReportOutline {

    /**
     * The text used in a section heading whenever its declaration check was skipped.
     */
    public static final String DETECTION_DISABLED = "<detection disabled>";

    private static final String MISSING_ENTITY_DECLARATIONS_TITLE = "Missing entity declarations";

    private static final String MISPLACED_ENTITY_DECLARATIONS_TITLE = "Misplaced entity declarations";

    @Nonnull
    private final DeclarationReport report;

    @Nonnull
    private final Section missingSection;

    @Nonnull
    private final Section misplacedSection;

    private DeclarationReportOutline(@Nonnull DeclarationReport report) {
        this.report = report;
        MissingDeclarationReport missing = report.getMissing();
        this.missingSection = new Section(MISSING_ENTITY_DECLARATIONS_TITLE, missing.getStatus(), missing.size());
        MisplacedDeclarationReport misplaced = report.getMisplaced();
        this.misplacedSection = new Section(MISPLACED_ENTITY_DECLARATIONS_TITLE, misplaced.getStatus(), misplaced.size());
    }

    /**
     * Creates an outline from the supplied declaration report.
     *
     * @param report the declaration report
     * @return an outline containing the report's sections and findings
     * @throws NullPointerException if {@code report} is {@code null}
     */
    @Nonnull
    public static DeclarationReportOutline of(@Nonnull DeclarationReport report) {
        return new DeclarationReportOutline(checkNotNull(report));
    }

    /**
     * Resolves the display label of the ownership rule recorded by a misplaced finding.
     *
     * @param finding the finding whose ownership rule is to be named
     * @return the label of the currently registered rule with the recorded identifier, or the
     *         identifier itself if that rule is no longer registered
     * @throws NullPointerException if {@code finding} is {@code null}
     */
    @Nonnull
    public static String ruleLabelOf(@Nonnull MisplacedDeclarationFinding finding) {
        checkNotNull(finding);
        String ruleId = finding.getOwnershipRuleId();
        return OwnershipRules.registered().stream()
                .filter(rule -> rule.getId().equals(ruleId))
                .map(rule -> rule.getDisplay().getLabel())
                .findFirst()
                .orElse(ruleId);
    }

    /**
     * Formats a count for display in a report using English thousands separators.
     *
     * @param count the count to format
     * @return the formatted count, such as {@code 2,043}
     */
    @Nonnull
    public static String formatCount(long count) {
        return String.format(Locale.ENGLISH, "%,d", count);
    }

    /**
     * Gets the two sections in their display order.
     *
     * @return an immutable list containing the missing-declaration section followed by the
     *         misplaced-declaration section
     */
    @Nonnull
    public ImmutableList<Section> getSections() {
        return ImmutableList.of(missingSection, misplacedSection);
    }

    /**
     * Gets the presentation metadata for missing declarations.
     *
     * @return the first section in the outline
     */
    @Nonnull
    public Section getMissingSection() {
        return missingSection;
    }

    /**
     * Gets the presentation metadata for misplaced declarations.
     *
     * @return the second section in the outline
     */
    @Nonnull
    public Section getMisplacedSection() {
        return misplacedSection;
    }

    /**
     * Gets the missing-declaration findings in report order.
     *
     * @return the immutable missing-declaration findings
     */
    @Nonnull
    public ImmutableList<MissingDeclarationFinding> getMissing() {
        return report.getMissing().getFindings();
    }

    /**
     * Gets the misplaced-declaration findings in report order.
     *
     * @return the immutable misplaced-declaration findings
     */
    @Nonnull
    public ImmutableList<MisplacedDeclarationFinding> getMisplaced() {
        return report.getMisplaced().getFindings();
    }

    /**
     * Determines whether either section contains at least one finding.
     *
     * <p>A skipped check does not by itself count as a finding.
     *
     * @return {@code true} if either section has findings; {@code false} otherwise
     */
    public boolean hasFindings() {
        return !report.isEmpty();
    }

    /**
     * Immutable presentation metadata for one kind of declaration finding.
     */
    public static final class Section {

        @Nonnull
        private final String title;

        @Nonnull
        private final DeclarationCheckStatus status;

        private final int count;

        private Section(@Nonnull String title,
                        @Nonnull DeclarationCheckStatus status,
                        int count) {
            this.title = title;
            this.status = status;
            this.count = count;
        }

        /**
         * Gets the section title.
         *
         * @return the title, such as {@code Missing entity declarations}
         */
        @Nonnull
        public String getTitle() {
            return title;
        }

        /**
         * Gets the execution status.
         *
         * @return whether the check executed or skipped.
         */
        @Nonnull
        public DeclarationCheckStatus getStatus() {
            return status;
        }

        /**
         * Gets the total number of findings in this section.
         *
         * @return the number of findings; always {@code 0} if the check was skipped
         */
        public int getCount() {
            return count;
        }

        /**
         * Gets the display text based on the check status. If the check was executed,
         * the text shows the issue count; otherwise, it shows an informational message.
         *
         * @return the formatted finding count if the check executed; otherwise
         *         {@link #DETECTION_DISABLED}
         */
        @Nonnull
        public String getStatusText() {
            return status.isExecuted() ? formatCount(count) : DETECTION_DISABLED;
        }

        /**
         * Gets the section heading.
         *
         * @return the title followed by the status text, such as
         *         {@code Missing entity declarations: 12}
         */
        @Nonnull
        public String getHeading() {
            return title + ": " + getStatusText();
        }
    }
}
