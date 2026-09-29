package org.protege.editor.owl.model.declaration;

import com.google.common.collect.ImmutableMap;
import org.semanticweb.owlapi.model.OWLOntologyID;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Maps a string key to the single candidate ontology that claims it.
 *
 * <p>Ownership rules use this index to turn values extracted from entity identifiers, such as an
 * IRI namespace or OBO ID space, into ontology identifiers. Keys are matched exactly and are
 * case-sensitive; a rule that needs normalization must perform it before building or querying the
 * index.
 *
 * <p>A key is retained only when exactly one distinct ontology claims it. If two different
 * ontologies produce the same key, that key is removed and {@link #lookup(String)} returns an empty
 * optional. Repeated occurrences of the same ontology do not make its key ambiguous. Refusing to
 * choose between competing claims prevents a heuristic rule from reporting an arbitrary owner.
 *
 * @author Josef Hardi
 */
final class OntologyKeyIndex {

    @Nonnull
    private final ImmutableMap<String, OWLOntologyID> byKey;

    private OntologyKeyIndex(@Nonnull Map<String, OWLOntologyID> byKey) {
        this.byKey = ImmutableMap.copyOf(byKey);
    }

    /**
     * Builds an index by deriving a key from each candidate ontology.
     *
     * <p>An ontology is omitted when {@code keyOf} returns an empty optional or an empty string.
     * Keys claimed by more than one distinct ontology are also omitted.
     *
     * @param ontologies the candidate ontology identifiers to index
     * @param keyOf the function that returns an ontology's key, or an empty optional to omit it
     * @return an index containing only non-empty, unambiguous keys
     * @throws NullPointerException if {@code ontologies} or {@code keyOf} is {@code null}
     */
    @Nonnull
    static OntologyKeyIndex over(@Nonnull Collection<OWLOntologyID> ontologies,
                                 @Nonnull Function<OWLOntologyID, Optional<String>> keyOf) {
        checkNotNull(ontologies);
        checkNotNull(keyOf);
        Map<String, OWLOntologyID> claims = new HashMap<>();
        Set<String> ambiguous = new HashSet<>();
        for (OWLOntologyID ontology : ontologies) {
            keyOf.apply(ontology)
                    .filter(key -> !key.isEmpty())
                    .ifPresent(key -> claim(claims, ambiguous, key, ontology));
        }
        ambiguous.forEach(claims::remove);
        return new OntologyKeyIndex(claims);
    }

    /**
     * Returns the unique ontology associated with an exact key.
     *
     * @param key the case-sensitive key to look up
     * @return the ontology associated with {@code key}, or an empty optional if the key was
     *         unclaimed, empty, or claimed by multiple ontologies
     * @throws NullPointerException if {@code key} is {@code null}
     */
    @Nonnull
    Optional<OWLOntologyID> lookup(@Nonnull String key) {
        checkNotNull(key);
        return Optional.ofNullable(byKey.get(key));
    }

    /** Records a claim and marks the key as ambiguous if a different ontology claimed it first. */
    private static void claim(Map<String, OWLOntologyID> claims,
                              Set<String> ambiguous,
                              String key,
                              OWLOntologyID ontology) {
        OWLOntologyID existing = claims.put(key, ontology);
        if (existing != null && !existing.equals(ontology)) {
            ambiguous.add(key);
        }
    }
}
