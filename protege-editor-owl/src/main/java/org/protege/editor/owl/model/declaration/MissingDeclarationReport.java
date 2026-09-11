package org.protege.editor.owl.model.declaration;

import com.google.common.collect.ImmutableList;

import javax.annotation.Nonnull;
import java.util.Collection;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Represents the result of checking an ontology for missing entity declarations.
 *
 * <p>A report contains all missing declaration findings produced by a single check. Findings can
 * be retrieved collectively or filtered by severity.
 *
 * @author Josef Hardi
 */
public class MissingDeclarationReport {

    private static final MissingDeclarationReport EMPTY =
            new MissingDeclarationReport(ImmutableList.of());

    @Nonnull
    private final ImmutableList<MissingDeclarationFinding> findings;

    private MissingDeclarationReport(@Nonnull ImmutableList<MissingDeclarationFinding> findings) {
        this.findings = findings;
    }

    /**
     * Creates a report containing the specified missing declaration findings.
     *
     * @param findings the findings to include in the report
     * @return a report containing the specified findings, or the shared empty report if there are
     *         no findings
     */
    @Nonnull
    public static MissingDeclarationReport get(
            @Nonnull Collection<MissingDeclarationFinding> findings) {
        checkNotNull(findings);
        return findings.isEmpty() ? EMPTY : new MissingDeclarationReport(ImmutableList.copyOf(findings));
    }

    /**
     * Gets all missing declaration findings in this report.
     *
     * @return the findings in their report order
     */
    @Nonnull
    public ImmutableList<MissingDeclarationFinding> getFindings() {
        return findings;
    }

    /**
     * Gets the missing declaration findings with the specified severity.
     *
     * <p>The returned findings preserve their order in the complete report.
     *
     * @param severity the severity by which to filter the findings
     * @return the findings with the specified severity
     */
    @Nonnull
    public ImmutableList<MissingDeclarationFinding> getFindings(@Nonnull DeclarationSeverity severity) {
        checkNotNull(severity);
        ImmutableList.Builder<MissingDeclarationFinding> selected = ImmutableList.builder();
        for (MissingDeclarationFinding finding : findings) {
            if (finding.getSeverity() == severity) {
                selected.add(finding);
            }
        }
        return selected.build();
    }

    /**
     * Determines whether this report contains no missing declaration findings.
     *
     * @return {@code true} if this report contains no findings, otherwise {@code false}
     */
    public boolean isEmpty() {
        return findings.isEmpty();
    }

    /**
     * Gets the number of missing declaration findings in this report.
     *
     * @return the number of findings
     */
    public int size() {
        return findings.size();
    }
}
