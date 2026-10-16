package org.protege.editor.owl.model.declaration;

import com.google.common.collect.ImmutableSet;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyID;

import javax.annotation.Nonnull;
import java.util.Optional;
import java.util.Collection;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Finds the unique earliest mention of an entity in an import closure.
 *
 * <p>An imported mentioning ontology precedes its importer. Siblings and import cycles do not
 * break ties: if there is no unique earliest mention, the strategy returns no suggestion.
 * One strategy indexes a closure once and is reused for all entities in a batch.
 */
public final class FirstMentionStrategy {

    private final ImportClosureView closure;

    FirstMentionStrategy(@Nonnull ImportClosureView closure) {
        this.closure = checkNotNull(closure);
    }

    /** Indexes the current closure once; create a new strategy after ontology changes. */
    @Nonnull
    public static FirstMentionStrategy over(@Nonnull OWLOntology root) {
        return new FirstMentionStrategy(DeclarationIndex.over(checkNotNull(root)));
    }

    /** Reuses a caller-supplied closure without choosing an arbitrary root ontology. */
    @Nonnull
    public static FirstMentionStrategy over(@Nonnull Collection<OWLOntology> closure) {
        return new FirstMentionStrategy(DeclarationIndex.over(closure));
    }

    /** Returns the unique first mention, or empty when absent or ambiguous. */
    @Nonnull
    public Optional<OWLOntologyID> resolve(@Nonnull OWLEntity entity) {
        ImmutableSet<OWLOntologyID> mentioners = closure.getMentioningOntologies(checkNotNull(entity));
        if (mentioners.size() == 1) {
            return Optional.of(mentioners.iterator().next());
        }
        OWLOntologyID first = null;
        for (OWLOntologyID candidate : mentioners) {
            if (nothingMentioningIsBelow(candidate, mentioners)) {
                if (first != null) {
                    return Optional.empty();
                }
                first = candidate;
            }
        }
        return Optional.ofNullable(first);
    }

    private boolean nothingMentioningIsBelow(OWLOntologyID candidate,
                                            ImmutableSet<OWLOntologyID> mentioners) {
        for (OWLOntologyID other : mentioners) {
            if (!other.equals(candidate) && isBelow(other, candidate)) {
                return false;
            }
        }
        return true;
    }

    /** A cycle makes neither ontology strictly earlier than the other. */
    private boolean isBelow(OWLOntologyID lower, OWLOntologyID upper) {
        return closure.reaches(upper, lower) && !closure.reaches(lower, upper);
    }
}
