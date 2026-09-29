package org.protege.editor.owl.model.declaration;

import com.google.common.collect.ImmutableList;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Contains potentially misplaced declaration findings.
 *
 * @author Josef Hardi
 */
public class MisplacedDeclarationReport {

    private static final Map<DeclarationCheckStatus, MisplacedDeclarationReport> EMPTY =
            emptyReports();

    @Nonnull
    private final DeclarationCheckStatus status;

    @Nonnull
    private final ImmutableList<MisplacedDeclarationFinding> findings;

    private MisplacedDeclarationReport(@Nonnull DeclarationCheckStatus status,
                                       @Nonnull ImmutableList<MisplacedDeclarationFinding> findings) {
        this.status = status;
        this.findings = findings;
    }

    private static Map<DeclarationCheckStatus, MisplacedDeclarationReport> emptyReports() {
        Map<DeclarationCheckStatus, MisplacedDeclarationReport> reports =
                new EnumMap<>(DeclarationCheckStatus.class);
        for (DeclarationCheckStatus status : DeclarationCheckStatus.values()) {
            reports.put(status, new MisplacedDeclarationReport(status, ImmutableList.of()));
        }
        return reports;
    }

    /**
     * Creates a report from a collection of findings.
     *
     * @param findings the findings to include in the report
     * @return the new report
     */
    @Nonnull
    public static MisplacedDeclarationReport get(
            @Nonnull Collection<MisplacedDeclarationFinding> findings) {
        checkNotNull(findings);
        return findings.isEmpty()
                ? EMPTY.get(DeclarationCheckStatus.EXECUTED)
                : new MisplacedDeclarationReport(DeclarationCheckStatus.EXECUTED, ImmutableList.copyOf(findings));
    }

    /**
     * Gets the report where a check was skipped.
     *
     * @param status the reason the check did not run
     * @return the shared empty report carrying that reason
     * @throws IllegalArgumentException if {@code status} is {@link DeclarationCheckStatus#EXECUTED}
     */
    @Nonnull
    public static MisplacedDeclarationReport skipped(@Nonnull DeclarationCheckStatus status) {
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
     * Gets the findings in this report.
     *
     * @return the findings in their report order
     */
    @Nonnull
    public ImmutableList<MisplacedDeclarationFinding> getFindings() {
        return findings;
    }

    /**
     * Checks whether this report has no findings.
     *
     * @return {@code true} if this report has no findings
     */
    public boolean isEmpty() {
        return findings.isEmpty();
    }

    /**
     * Gets the number of findings in this report.
     *
     * @return the number of findings
     */
    public int size() {
        return findings.size();
    }
}
