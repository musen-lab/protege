package org.protege.editor.owl.model.declaration;

import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLOntologyID;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Optional;

/**
 * Finds the ontology that owns an entity identifier.
 *
 * <p>Resolution happens in two phases. {@link #compile(Collection)} builds whatever index the rule
 * needs over one import closure, and the resolver it returns answers for each entity in that
 * closure. Every entity of a closure is resolved, so a resolver does lookups and nothing more.
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
     * @param ontologies the identifiers of the ontologies in the closure
     * @return a resolver that answers for entities of that closure
     */
    @Nonnull
    Resolver compile(@Nonnull Collection<OWLOntologyID> ontologies);

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
