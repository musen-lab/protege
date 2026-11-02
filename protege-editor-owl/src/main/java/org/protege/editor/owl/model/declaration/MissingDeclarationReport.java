package org.protege.editor.owl.model.declaration;

import com.google.common.collect.ImmutableList;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;
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

    private static final Map<DeclarationCheckStatus, MissingDeclarationReport> EMPTY =
            emptyReports();

    @Nonnull
    private final DeclarationCheckStatus status;

    @Nonnull
    private final ImmutableList<MissingDeclarationFinding> findings;

    private MissingDeclarationReport(@Nonnull DeclarationCheckStatus status,
                                     @Nonnull ImmutableList<MissingDeclarationFinding> findings) {
        this.status = status;
        this.findings = findings;
    }

    private static Map<DeclarationCheckStatus, MissingDeclarationReport> emptyReports() {
        Map<DeclarationCheckStatus, MissingDeclarationReport> reports =
                new EnumMap<>(DeclarationCheckStatus.class);
        for (DeclarationCheckStatus status : DeclarationCheckStatus.values()) {
            reports.put(status, new MissingDeclarationReport(status, ImmutableList.of()));
        }
        return reports;
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
        return findings.isEmpty()
                ? EMPTY.get(DeclarationCheckStatus.EXECUTED)
                : new MissingDeclarationReport(DeclarationCheckStatus.EXECUTED, ImmutableList.copyOf(findings));
    }

    /**
     * Gets the report where a check was skipped.
     *
     * @param status the reason the check did not run
     * @return the shared empty report carrying that reason
     * @throws IllegalArgumentException if {@code status} is {@link DeclarationCheckStatus#EXECUTED}
     */
    @Nonnull
    public static MissingDeclarationReport skipped(@Nonnull DeclarationCheckStatus status) {
        checkNotNull(status);
        checkArgument(!status.isExecuted(), "A check was skipped.");
        return EMPTY.get(status);
    }

    /**
     * Gets whether the check ran and, when it did not, why.
     *
     * @return the check status
     */
    @Nonnull
    public DeclarationCheckStatus getStatus() {
        return status;
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
