package org.protege.editor.owl.model.declaration;

import com.google.common.collect.ImmutableList;

import javax.annotation.Nonnull;
import java.util.Collection;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Contains potentially misplaced declaration findings.
 *
 * @author Josef Hardi
 */
public class MisplacedDeclarationReport {

    private static final MisplacedDeclarationReport EMPTY =
            new MisplacedDeclarationReport(ImmutableList.of());

    @Nonnull
    private final ImmutableList<MisplacedDeclarationFinding> findings;

    private MisplacedDeclarationReport(@Nonnull ImmutableList<MisplacedDeclarationFinding> findings) {
        this.findings = findings;
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
        return findings.isEmpty() ? EMPTY : new MisplacedDeclarationReport(ImmutableList.copyOf(findings));
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
