package org.protege.editor.owl.model.declaration;

import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLOntologyID;

import javax.annotation.Nonnull;
import java.util.Optional;

/**
 * Finds the ontology that owns an entity identifier.
 *
 * <p>Resolution happens in two phases. {@link #compile(ImportClosureView)} builds whatever index
 * the rule needs over one import closure, and the resolver it returns answers for each entity in
 * that closure. Every entity of a closure is resolved, so a resolver does lookups and nothing
 * more.
 *
 * <p>A rule reports no owner rather than guessing one. Where an identifier could belong to more
 * than one ontology, the rule resolves nothing.
 *
 * @author Josef Hardi
 */
public interface OwnershipRule {

    /**
     * Gets the identifier that names this rule wherever it is stored or recorded.
     *
     * <p>The identifier is the key the rule's setting is stored under and the value a finding
     * carries, so it is fixed for the life of the rule and outlives any renaming of the rule.
     *
     * @return the rule identifier
     */
    @Nonnull
    String getId();

    /**
     * Gets the wording that describes this rule to a user.
     *
     * @return the display wording
     */
    @Nonnull
    OwnershipRuleDisplay getDisplay();

    /**
     * Builds a resolver for one import closure.
     *
     * @param closure the closure to resolve against
     * @return a resolver that answers for entities of that closure
     */
    @Nonnull
    Resolver compile(@Nonnull ImportClosureView closure);

    /**
     * Decides whether a declaration in {@code candidate} should be accepted as though it were in
     * {@code owner}.
     *
     * <p>The declaration checker calls this method when {@code owner} does not declare the entity
     * but {@code candidate} does. Returning {@code true} allows {@code candidate} to declare the
     * entity on behalf of {@code owner}. Returning {@code false} treats the declaration in
     * {@code candidate} as misplaced.
     *
     * <p>The default implementation accepts documents in the same ontology-IRI family. This
     * supports projects that divide one logical ontology into component documents. For example, a
     * rule may select {@code go.owl} as the owner while the entity is correctly declared in
     * {@code go/components/terms.owl}.
     *
     * <p>A rule can override this method to define a different substitute policy. For example,
     * {@link FirstMentionRule} selects one specific document from the import graph and rejects
     * every substitute. A different document cannot replace the selected document merely because
     * their ontology IRIs have related paths.
     *
     * @param owner the ontology this rule selected as the expected owner
     * @param candidate an ontology that declares the entity
     * @return {@code true} if a declaration in {@code candidate} is equivalent to a declaration
     *         in {@code owner}; {@code false} if the candidate should be treated as foreign
     */
    default boolean standsInForOwner(@Nonnull OWLOntologyID owner, @Nonnull OWLOntologyID candidate) {
        return DocumentFamily.sameFamily(owner, candidate);
    }

    /**
     * Finds owning ontologies within the one import closure it was compiled over.
     */
    interface Resolver {

        /**
         * Finds the ontology that owns an entity identifier.
         *
         * @param entity the entity to look up
         * @return the owning ontology, or an empty value if this rule finds none
         */
        @Nonnull
        Optional<OWLOntologyID> resolve(@Nonnull OWLEntity entity);
    }
}
