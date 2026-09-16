package org.protege.editor.owl.model.declaration;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.protege.editor.owl.model.util.OboUtilities;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLOntologyID;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Finds the ontology that owns an entity ID.
 *
 * <p>The OBO ID rule takes priority over the namespace rule. No owner is returned when an ID could
 * belong to more than one ontology.
 *
 * @author Josef Hardi
 */
final class OwnerIndex {

    private static final String OWL_SUFFIX = ".owl";

    @Nonnull
    private final ImmutableMap<String, OWLOntologyID> byOntologyIri;

    @Nonnull
    private final ImmutableMap<String, OWLOntologyID> byShortName;

    @Nonnull
    private final ImmutableSet<OwnershipRule> enabledRules;

    private OwnerIndex(@Nonnull Map<String, OWLOntologyID> byOntologyIri,
                       @Nonnull Map<String, OWLOntologyID> byShortName,
                       @Nonnull Set<OwnershipRule> enabledRules) {
        this.byOntologyIri = ImmutableMap.copyOf(byOntologyIri);
        this.byShortName = ImmutableMap.copyOf(byShortName);
        this.enabledRules = ImmutableSet.copyOf(enabledRules);
    }

    /**
     * Creates an index for finding the ontology that owns an entity ID.
     *
     * <p>Ontologies are indexed by IRI and short name. Anonymous ontologies and names shared by
     * multiple ontologies are excluded.
     *
     * @param ontologies the ontologies to index
     * @param enabledRules the rules to use when finding owners
     * @return the new owner index
     */
    @Nonnull
    static OwnerIndex over(@Nonnull Collection<OWLOntologyID> ontologies,
                           @Nonnull Set<OwnershipRule> enabledRules) {
        checkNotNull(ontologies);
        checkNotNull(enabledRules);
        Map<String, OWLOntologyID> byOntologyIri = new HashMap<>();
        Map<String, OWLOntologyID> byShortName = new HashMap<>();
        Set<String> ambiguousOntologyIris = new HashSet<>();
        Set<String> ambiguousShortNames = new HashSet<>();
        for (OWLOntologyID ontology : ontologies) {
            Optional<String> ontologyIri = ontologyIriOf(ontology);
            if (ontologyIri.isPresent()) {
                // Ontology IRI index
                String cleanOntologyIri = stripTrailingSeparator(ontologyIri.get());
                claim(byOntologyIri, ambiguousOntologyIris, cleanOntologyIri, ontology);
                // Ontology short name index
                Optional<String> shortName = shortNameOf(ontologyIri.get());
                shortName.ifPresent(s -> claim(byShortName, ambiguousShortNames, s, ontology));
            }
        }
        // Any colliding ontology IRI or short name is dropped from the index
        ambiguousOntologyIris.forEach(byOntologyIri::remove);
        ambiguousShortNames.forEach(byShortName::remove);
        return new OwnerIndex(byOntologyIri, byShortName, enabledRules);
    }

    /**
     * Finds the ontology that owns an entity ID.
     *
     * @param entity the entity to look up
     * @return the owner, or an empty value if no owner can be found
     */
    @Nonnull
    Optional<DeclarationOwner> resolve(@Nonnull OWLEntity entity) {
        checkNotNull(entity);
        if (enabledRules.contains(OwnershipRule.OBO_IDENTIFIER)) {
            Optional<OWLOntologyID> owner = resolveByIdSpace(entity);
            if (owner.isPresent()) {
                return Optional.of(DeclarationOwner.get(owner.get(), OwnershipRule.OBO_IDENTIFIER));
            }
        }
        if (enabledRules.contains(OwnershipRule.NAMESPACE)) {
            Optional<OWLOntologyID> owner = resolveByNamespace(entity);
            if (owner.isPresent()) {
                return Optional.of(DeclarationOwner.get(owner.get(), OwnershipRule.NAMESPACE));
            }
        }
        return Optional.empty();
    }

    private Optional<OWLOntologyID> resolveByIdSpace(OWLEntity entity) {
        // OboUtilities reads an ID space that contains an underscore, such as APOLLO_SV, correctly.
        return OboUtilities.getOboIdSpaceFromIri(entity.getIRI())
                .map(idSpace -> idSpace.toLowerCase(Locale.ROOT))
                .map(byShortName::get); // check against the short name index
    }

    private Optional<OWLOntologyID> resolveByNamespace(OWLEntity entity) {
        String namespace = stripTrailingSeparator(entity.getIRI().getNamespace());
        return Optional.ofNullable(byOntologyIri.get(namespace));   // check against the ontology iri index
    }

    private static void claim(Map<String, OWLOntologyID> claims,
                              Set<String> ambiguous,
                              String name,
                              OWLOntologyID ontology) {
        OWLOntologyID existing = claims.put(name, ontology);
        if (existing != null && !existing.equals(ontology)) {
            ambiguous.add(name);
        }
    }

    private static Optional<String> shortNameOf(String ontologyIri) {
        int lastSlash = ontologyIri.lastIndexOf('/');
        String segment = lastSlash < 0 ? ontologyIri : ontologyIri.substring(lastSlash + 1);
        if (segment.endsWith(OWL_SUFFIX)) {
            segment = segment.substring(0, segment.length() - OWL_SUFFIX.length());
        }
        return segment.isEmpty() ? Optional.empty() : Optional.of(segment.toLowerCase(Locale.ROOT));
    }

    private static String stripTrailingSeparator(String iri) {
        return iri.endsWith("#") || iri.endsWith("/") ? iri.substring(0, iri.length() - 1) : iri;
    }

    private static Optional<String> ontologyIriOf(OWLOntologyID id) {
        return id.isAnonymous() ? Optional.empty() : Optional.of(id.getOntologyIRI().get().toString());
    }
}
