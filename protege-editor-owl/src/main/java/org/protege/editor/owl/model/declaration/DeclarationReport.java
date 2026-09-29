package org.protege.editor.owl.model.declaration;

import javax.annotation.Nonnull;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Contains the missing and misplaced declarations found in an ontology and its imports.
 *
 * @author Josef Hardi
 */
public class DeclarationReport {

    @Nonnull
    private final MissingDeclarationReport missing;

    @Nonnull
    private final MisplacedDeclarationReport misplaced;

    private DeclarationReport(@Nonnull MissingDeclarationReport missing,
                              @Nonnull MisplacedDeclarationReport misplaced) {
        this.missing = missing;
        this.misplaced = misplaced;
    }

    /**
     * Creates a report from missing and misplaced declaration findings.
     *
     * @param missing the missing-declaration findings
     * @param misplaced the misplaced-declaration findings
     * @return the new report
     */
    @Nonnull
    public static DeclarationReport get(@Nonnull MissingDeclarationReport missing,
                                        @Nonnull MisplacedDeclarationReport misplaced) {
        return new DeclarationReport(checkNotNull(missing), checkNotNull(misplaced));
    }

    /**
     * Gets the missing declaration findings.
     *
     * @return the missing-declaration findings
     */
    @Nonnull
    public MissingDeclarationReport getMissing() {
        return missing;
    }

    /**
     * Gets the misplaced declaration findings.
     *
     * @return the misplaced-declaration findings
     */
    @Nonnull
    public MisplacedDeclarationReport getMisplaced() {
        return misplaced;
    }

    /**
     * Checks whether the report has no findings.
     *
     * @return {@code true} if both reports are empty, otherwise {@code false}
     */
    public boolean isEmpty() {
        return missing.isEmpty() && misplaced.isEmpty();
    }
}
