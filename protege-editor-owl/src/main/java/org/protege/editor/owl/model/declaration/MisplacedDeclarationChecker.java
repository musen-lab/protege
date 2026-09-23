package org.protege.editor.owl.model.declaration;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyID;

import javax.annotation.Nonnull;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Finds entities declared outside the ontology that owns their IDs.
 *
 * <p>A declaration is reported when the owning ontology is loaded but does not declare the entity,
 * and an ontology from another document family does. Ownership is inferred from the enabled
 * {@link OwnershipRule ownership rules}, so all findings are warnings.
 *
 * @author Josef Hardi
 */
public class MisplacedDeclarationChecker {

    private static final Comparator<MisplacedDeclarationFinding> BY_NAME_THEN_KIND =
            Comparator.comparing((MisplacedDeclarationFinding finding) -> finding.getEntity().getIRI().toString())
                    .thenComparing(finding -> finding.getEntityType().getName());

    @Nonnull
    private final Supplier<ImmutableList<OwnershipRule>> enabledRules;

    /**
     * Creates a checker that uses the ownership rules enabled in the preferences.
     */
    public MisplacedDeclarationChecker() {
        this(() -> MisplacedDeclarationPreferences.getInstance().getEnabledRules());
    }

    /**
     * Creates a checker that uses the supplied ownership rules.
     *
     * @param enabledRules supplies the rules for each check, in the order they are consulted
     */
    MisplacedDeclarationChecker(@Nonnull Supplier<ImmutableList<OwnershipRule>> enabledRules) {
        this.enabledRules = checkNotNull(enabledRules);
    }

    /**
     * Finds misplaced declarations in an ontology and its imports.
     *
     * @param ontology the ontology to check
     * @return the findings, ordered by entity IRI and type
     */
    @Nonnull
    public MisplacedDeclarationReport check(@Nonnull OWLOntology ontology) {
        checkNotNull(ontology);
        return check(DeclarationIndex.over(ontology));
    }

    /**
     * Finds misplaced declarations in an existing index.
     *
     * @param index the declaration index to check
     * @return the findings, ordered by entity IRI and type
     */
    @Nonnull
    MisplacedDeclarationReport check(@Nonnull DeclarationIndex index) {
        checkNotNull(index);
        // Read the settings once, so a value changed mid-run cannot split the result between rules.
        ImmutableList<OwnershipRule> rules = enabledRules.get();
        if (rules.isEmpty()) {
            return MisplacedDeclarationReport.get(ImmutableList.of());
        }
        OwnershipPolicy policy = OwnershipPolicy.over(rules, index);
        return MisplacedDeclarationReport.get(index.getEntities()
                .stream()
                .filter(entity -> !StandardVocabulary.contains(entity))
                .map(entity -> findingFor(entity, policy, index))
                .flatMap(Optional::stream)
                .sorted(BY_NAME_THEN_KIND)
                .collect(Collectors.toList()));
    }

    /**
     * Finds the misplaced declaration of an entity, if it has one.
     */
    @Nonnull
    private static Optional<MisplacedDeclarationFinding> findingFor(@Nonnull OWLEntity entity,
                                                                    @Nonnull OwnershipPolicy policy,
                                                                    @Nonnull DeclarationIndex index) {
        // Step one: identify the ontology that owns the entity.
        Optional<DeclarationOwner> owner = policy.resolve(entity);
        if (owner.isEmpty()) {
            return Optional.empty();
        }
        OWLOntologyID owningOntology = owner.get().getOntology();

        // Step two: identify the ontologies that declare the entity.
        ImmutableSet<OWLOntologyID> declaringOntologies = index.getDeclaringOntologies(entity);
        if (declaringOntologies.contains(owningOntology)) {
            return Optional.empty();    // nothing is misplaced if the owner declares the entity
        }

        // Step three: identify the declarers outside the owner's family. Note: A project may spread
        // its terms across documents, so obo/go/components/terms.owl is in the family of obo/go.owl,
        // and a GO entity declared there is not misplaced.
        ImmutableSet<OWLOntologyID> foreignDeclarers = foreignDeclarersOf(owner.get(), declaringOntologies);
        if (foreignDeclarers.isEmpty()) {
            return Optional.empty();    // nothing is misplaced, the owner's family declared it or nothing did
        }
        return Optional.of(MisplacedDeclarationFinding.get(entity,
                owningOntology,
                foreignDeclarers,
                owner.get().getRuleId()));
    }

    private static ImmutableSet<OWLOntologyID> foreignDeclarersOf(DeclarationOwner owner,
                                                                  Set<OWLOntologyID> declaringOntologies) {
        // The rule that chose the owner decides what stands in for it.
        return ImmutableSet.copyOf(
                declaringOntologies.stream()
                        .filter(declaringOntology -> !owner.isStoodInForBy(declaringOntology))
                        .collect(Collectors.toList()));
    }
}
