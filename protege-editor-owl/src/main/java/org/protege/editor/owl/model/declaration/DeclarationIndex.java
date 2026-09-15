package org.protege.editor.owl.model.declaration;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.Imports;

import javax.annotation.Nonnull;
import java.util.LinkedHashMap;
import java.util.Map;

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
    private final ImmutableMap<OWLEntity, ImmutableSet<OWLOntologyID>> declaredBy;

    @Nonnull
    private final ImmutableMap<OWLEntity, ImmutableSet<OWLOntologyID>> usedBy;

    private DeclarationIndex(@Nonnull ImmutableMap<OWLEntity, ImmutableSet<OWLOntologyID>> declaredBy,
                             @Nonnull ImmutableMap<OWLEntity, ImmutableSet<OWLOntologyID>> usedBy) {
        this.declaredBy = checkNotNull(declaredBy);
        this.usedBy = checkNotNull(usedBy);
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
        Map<OWLEntity, ImmutableSet.Builder<OWLOntologyID>> declaredBy = new LinkedHashMap<>();
        Map<OWLEntity, ImmutableSet.Builder<OWLOntologyID>> usedBy = new LinkedHashMap<>();
        // getImportsClosure() includes the ontology itself and visits each import once, so a
        // cycle between two ontologies ends rather than repeating.
        for (OWLOntology importOntology : ontology.getImportsClosure()) {
            OWLOntologyID importOntologyId = importOntology.getOntologyID();
            for (OWLDeclarationAxiom declaration : importOntology.getAxioms(AxiomType.DECLARATION)) {
                record(declaredBy, declaration.getEntity(), importOntologyId);
            }
            for (OWLEntity entity : importOntology.getSignature(Imports.EXCLUDED)) {
                record(usedBy, entity, importOntologyId);
            }
        }
        return new DeclarationIndex(build(declaredBy), build(usedBy));
    }

    private static void record(Map<OWLEntity, ImmutableSet.Builder<OWLOntologyID>> collector,
                               OWLEntity entity,
                               OWLOntologyID ontologyId) {
        collector.computeIfAbsent(entity, key -> ImmutableSet.builder()).add(ontologyId);
    }

    private static ImmutableMap<OWLEntity, ImmutableSet<OWLOntologyID>> build(
            Map<OWLEntity, ImmutableSet.Builder<OWLOntologyID>> collector) {
        ImmutableMap.Builder<OWLEntity, ImmutableSet<OWLOntologyID>> built = ImmutableMap.builder();
        collector.forEach((entity, ontologyIds) -> built.put(entity, ontologyIds.build()));
        return built.build();
    }

    /**
     * Gets all entities used within the indexed import closure.
     *
     * @return the entities used within the import closure
     */
    @Nonnull
    ImmutableSet<OWLEntity> getEntities() {
        return usedBy.keySet();
    }

    /**
     * Gets the ontology identifiers that explicitly declare the specified entity.
     *
     * @param entity the entity to look up
     * @return the ontology identifiers that declare the entity, or an empty set if it is
     *         not declared
     */
    @Nonnull
    ImmutableSet<OWLOntologyID> getDeclaringOntologies(@Nonnull OWLEntity entity) {
        checkNotNull(entity);
        return declaredBy.getOrDefault(entity, ImmutableSet.of());
    }

    /**
     * Gets the ontology identifiers whose signatures contain the specified entity.
     *
     * @param entity the entity to look up
     * @return the ontology identifiers whose signatures contain the entity, or an empty
     *         set if none do
     */
    @Nonnull
    ImmutableSet<OWLOntologyID> getUsingOntologies(@Nonnull OWLEntity entity) {
        checkNotNull(entity);
        return usedBy.getOrDefault(entity, ImmutableSet.of());
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
