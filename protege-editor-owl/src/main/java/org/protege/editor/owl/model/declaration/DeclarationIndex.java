package org.protege.editor.owl.model.declaration;

import com.google.common.collect.ImmutableMap;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.Imports;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Indexes the entities declared and used within an ontology's import closure.
 *
 * <p>The index is built by traversing the import closure once. For each entity, it records the
 * ontologies that explicitly declare the entity and the ontologies whose signatures contain it.
 * Entities that are used but not declared are retained in the index so they can be identified as
 * missing declarations.
 *
 * <p>Entities are indexed by {@link OWLEntity}, preserving their entity type. The same IRI may
 * therefore be indexed separately as different entity types, such as a class and a named
 * individual.
 *
 * @author Josef Hardi
 */
class DeclarationIndex {

    @Nonnull
    private final ImmutableMap<OWLEntity, Set<OWLOntology>> declaredBy;

    @Nonnull
    private final ImmutableMap<OWLEntity, Set<OWLOntology>> usedBy;

    private DeclarationIndex(@Nonnull Map<OWLEntity, Set<OWLOntology>> declaredBy,
                             @Nonnull Map<OWLEntity, Set<OWLOntology>> usedBy) {
        this.declaredBy = ImmutableMap.copyOf(checkNotNull(declaredBy));
        this.usedBy = ImmutableMap.copyOf(checkNotNull(usedBy));
    }

    /**
     * Creates an index of the entities declared and used within the specified ontology's import
     * closure.
     *
     * @param ontology the ontology whose import closure is to be indexed
     * @return the declaration index
     */
    @Nonnull
    static DeclarationIndex over(@Nonnull OWLOntology ontology) {
        checkNotNull(ontology);
        Map<OWLEntity, Set<OWLOntology>> declaredBy = new LinkedHashMap<>();
        Map<OWLEntity, Set<OWLOntology>> usedBy = new LinkedHashMap<>();
        // getImportsClosure() includes the ontology itself and visits each import once, so a
        // cycle between two ontologies ends rather than repeating.
        for (OWLOntology importOntology : ontology.getImportsClosure()) {
            for (OWLDeclarationAxiom declaration : importOntology.getAxioms(AxiomType.DECLARATION)) {
                record(declaredBy, declaration.getEntity(), importOntology);
            }
            for (OWLEntity entity : importOntology.getSignature(Imports.EXCLUDED)) {
                record(usedBy, entity, importOntology);
            }
        }
        return new DeclarationIndex(declaredBy, usedBy);
    }

    private static void record(Map<OWLEntity, Set<OWLOntology>> collector,
                               OWLEntity entity,
                               OWLOntology ontology) {
        collector.computeIfAbsent(entity, key -> new LinkedHashSet<>()).add(ontology);
    }

    /**
     * Gets all entities used within the indexed import closure.
     *
     * @return the entities used within the import closure
     */
    @Nonnull
    Set<OWLEntity> getEntities() {
        return Collections.unmodifiableSet(usedBy.keySet());
    }

    /**
     * Gets the ontologies that explicitly declare the specified entity.
     *
     * @param entity the entity to look up
     * @return the ontologies that declare the entity, or an empty set if it is not declared
     */
    @Nonnull
    Set<OWLOntology> getDeclaringOntologies(@Nonnull OWLEntity entity) {
        checkNotNull(entity);
        return Collections.unmodifiableSet(declaredBy.getOrDefault(entity, Collections.emptySet()));
    }

    /**
     * Gets the ontologies whose signatures contain the specified entity.
     *
     * @param entity the entity to look up
     * @return the ontologies whose signatures contain the entity, or an empty set if none do
     */
    @Nonnull
    Set<OWLOntology> getUsingOntologies(@Nonnull OWLEntity entity) {
        checkNotNull(entity);
        return Collections.unmodifiableSet(usedBy.getOrDefault(entity, Collections.emptySet()));
    }

    /**
     * Determines whether the specified entity is explicitly declared by any ontology in the
     * indexed import closure.
     *
     * @param entity the entity to look up
     * @return {@code true} if at least one ontology declares the entity, otherwise {@code false}
     */
    boolean isDeclared(@Nonnull OWLEntity entity) {
        return !getDeclaringOntologies(entity).isEmpty();
    }
}
