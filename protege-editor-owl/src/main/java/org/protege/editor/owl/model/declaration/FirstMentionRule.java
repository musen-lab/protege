package org.protege.editor.owl.model.declaration;

import org.semanticweb.owlapi.model.OWLOntologyID;

import javax.annotation.Nonnull;

/**
 * Selects an entity's owner from the first ontology in the import chain that mentions it.
 *
 * <p>"First" is defined by the direction of the imports, not by iteration order. When one
 * mentioning ontology imports another, the imported ontology comes first. For example, if
 * {@code application.owl} imports {@code core.owl} and both ontologies mention {@code Customer},
 * this rule selects {@code core.owl}. If only {@code application.owl} mentions {@code Customer},
 * it selects {@code application.owl} instead. A mention is any use in an ontology's own axioms,
 * not necessarily a declaration.
 *
 * <p>The rule selects an owner only when there is one unique first mention. It returns no owner
 * when the earliest mentions occur in two sibling ontologies, because neither imports the other.
 * It also returns no owner when the first mentions are connected by an import cycle, because the
 * cycle gives them equal standing.
 *
 * <p>Unlike {@link IriPrefixRule} and {@link OboIdentifierRule}, this rule does not inspect the
 * entity or ontology IRIs. It can therefore resolve ownership in projects whose entities share a
 * namespace that does not identify any one ontology.
 *
 * @author Josef Hardi
 */
final class FirstMentionRule implements OwnershipRule {

    /** Stable identifier used to store this rule's preference and record its findings. */
    static final String ID = "misplaced.rule.use.first.mention";

    @Nonnull
    private static final OwnershipRuleDisplay DISPLAY = OwnershipRuleDisplay.get(
            "Decide ownership from the import closure",
            "Matches an entity to the first document in the closure that mentions it: a term used "
                    + "in core.owl but declared in sales.owl belongs to core.owl.");

    @Override
    @Nonnull
    public String getId() {
        return ID;
    }

    @Override
    @Nonnull
    public OwnershipRuleDisplay getDisplay() {
        return DISPLAY;
    }

    @Override
    @Nonnull
    public Resolver compile(@Nonnull ImportClosureView closure) {
        return new FirstMentionStrategy(closure)::resolve;
    }

    @Override
    public boolean standsInForOwner(@Nonnull OWLOntologyID owner, @Nonnull OWLOntologyID candidate) {
        return false;
    }
}
