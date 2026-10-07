package org.protege.editor.owl.model.declaration;

import org.semanticweb.owlapi.model.OWLOntology;

import javax.annotation.Nonnull;
import java.util.function.BooleanSupplier;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Checks an ontology and its imports for missing or misplaced entity declarations.
 *
 * <p>Nothing is reported unless automatic entity declarations are suppressed. Otherwise saving
 * writes a declaration into the saved document for every entity its closure leaves undeclared, so
 * a missing declaration would be reported and then written away, and the declaration so written
 * would be reported in turn as misplaced. Suppression is therefore the one setting that turns
 * declaration checking on, and the ownership rules govern only what is reported once it is on.
 *
 * @author Josef Hardi
 */
public class DeclarationChecker {

    @Nonnull
    private final MissingDeclarationChecker missingChecker;

    @Nonnull
    private final MisplacedDeclarationChecker misplacedChecker;

    @Nonnull
    private final BooleanSupplier suppressingAutomaticDeclarations;

    /**
     * Creates a checker for missing and misplaced declarations.
     */
    public DeclarationChecker() {
        this(new MissingDeclarationChecker(), new MisplacedDeclarationChecker(),
                () -> EntityDeclarationPreferences.getInstance().isSuppressingAutomaticDeclarations());
    }

    /**
     * Creates a checker that runs both the missing- and misplaced-declaration checks regardless of
     * the user's Axioms preferences.
     *
     * <p>The returned checker ignores the "Suppress automatic entity declarations" setting that
     * normally turns declaration checking on, and it consults every registered ownership rule
     * rather than only the enabled ones. It reads no preference and changes none, and like every
     * other checker it never modifies the ontology. This supports a manually invoked, read-only
     * declaration report that must run both checks even when the preferences would skip them.
     *
     * @return a checker that always runs both declaration checks
     */
    @Nonnull
    public static DeclarationChecker forcingAllChecks() {
        return new DeclarationChecker(
                new MissingDeclarationChecker(),
                new MisplacedDeclarationChecker(OwnershipRules::registered),
                () -> true);
    }

    /**
     * Creates a checker that uses the given declaration checkers.
     *
     * @param missingChecker checks for missing declarations
     * @param misplacedChecker checks for misplaced declarations
     * @param suppressingAutomaticDeclarations returns {@code true} when automatic entity
     *                                         declarations are suppressed
     */
    DeclarationChecker(@Nonnull MissingDeclarationChecker missingChecker,
                       @Nonnull MisplacedDeclarationChecker misplacedChecker,
                       @Nonnull BooleanSupplier suppressingAutomaticDeclarations) {
        this.missingChecker = checkNotNull(missingChecker);
        this.misplacedChecker = checkNotNull(misplacedChecker);
        this.suppressingAutomaticDeclarations = checkNotNull(suppressingAutomaticDeclarations);
    }

    /**
     * Checks an ontology and its imports for declaration problems.
     *
     * @param ontology the ontology to check
     * @return the missing and misplaced declaration findings, or both kinds reported as not run
     *         while automatic entity declarations are not suppressed
     */
    @Nonnull
    public DeclarationReport check(@Nonnull OWLOntology ontology) {
        checkNotNull(ontology);
        // Read once per run, so turning the setting on or off takes effect on the next run.
        if (!suppressingAutomaticDeclarations.getAsBoolean()) {
            return DeclarationReport.get(
                    MissingDeclarationReport.skipped(DeclarationCheckStatus.SKIPPED),
                    MisplacedDeclarationReport.skipped(DeclarationCheckStatus.SKIPPED));
        }
        DeclarationIndex index = DeclarationIndex.over(ontology);
        return DeclarationReport.get(missingChecker.check(index), misplacedChecker.check(index));
    }
}
