package org.protege.editor.owl.model.declaration;

import org.semanticweb.owlapi.model.OWLOntology;

import javax.annotation.Nonnull;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Checks an ontology and its imports for missing or misplaced entity declarations.
 *
 * @author Josef Hardi
 */
public class DeclarationChecker {

    @Nonnull
    private final MissingDeclarationChecker missingChecker;

    @Nonnull
    private final MisplacedDeclarationChecker misplacedChecker;

    /**
     * Creates a checker for missing and misplaced declarations.
     */
    public DeclarationChecker() {
        this(new MissingDeclarationChecker(), new MisplacedDeclarationChecker());
    }

    /**
     * Creates a checker that uses the given declaration checkers.
     *
     * @param missingChecker checks for missing declarations
     * @param misplacedChecker checks for misplaced declarations
     */
    DeclarationChecker(@Nonnull MissingDeclarationChecker missingChecker,
                       @Nonnull MisplacedDeclarationChecker misplacedChecker) {
        this.missingChecker = checkNotNull(missingChecker);
        this.misplacedChecker = checkNotNull(misplacedChecker);
    }

    /**
     * Checks an ontology and its imports for declaration problems.
     *
     * @param ontology the ontology to check
     * @return the missing and misplaced declaration findings
     */
    @Nonnull
    public DeclarationReport check(@Nonnull OWLOntology ontology) {
        checkNotNull(ontology);
        DeclarationIndex index = DeclarationIndex.over(ontology);
        return DeclarationReport.get(missingChecker.check(index), misplacedChecker.check(index));
    }
}
