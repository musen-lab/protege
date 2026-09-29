package org.protege.editor.owl.model.declaration;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.Imports;

import javax.annotation.Nonnull;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Indexes the entities declared and used within an ontology's import closure.
 *
 * <p>The index is built by traversing the import closure once. For each entity, it records the
 * ontologies that explicitly declare the entity and the ontologies whose signatures contain it.
 * Entities that are used but not declared are retained in the index so they can be identified as
 * missing declarations. The same traversal records which ontologies each ontology imports, from
 * which the index derives reachability.
 *
 * <p>Entities are indexed by {@link OWLEntity}, preserving their entity type. The same IRI may
 * therefore be indexed separately as different entity types, such as a class and a named
 * individual.
 *
 * <p>The index serves as the {@link ImportClosureView} an ownership rule resolves against. Only the
 * members of that interface are reachable from a rule.
 *
 * @author Josef Hardi
 */
class DeclarationIndex implements ImportClosureView {

    @Nonnull
    private final ImmutableMap<OWLEntity, ImmutableSet<OWLOntologyID>> declaredBy;

    @Nonnull
    private final ImmutableMap<OWLEntity, ImmutableSet<OWLOntologyID>> usedBy;

    @Nonnull
    private final ImmutableSet<OWLOntologyID> ontologies;

    @Nonnull
    private final ImmutableMap<OWLOntologyID, ImmutableSet<OWLOntologyID>> reachable;

    private DeclarationIndex(@Nonnull ImmutableMap<OWLEntity, ImmutableSet<OWLOntologyID>> declaredBy,
                             @Nonnull ImmutableMap<OWLEntity, ImmutableSet<OWLOntologyID>> usedBy,
                             @Nonnull ImmutableSet<OWLOntologyID> ontologies,
                             @Nonnull ImmutableMap<OWLOntologyID, ImmutableSet<OWLOntologyID>> reachable) {
        this.declaredBy = checkNotNull(declaredBy);
        this.usedBy = checkNotNull(usedBy);
        this.ontologies = checkNotNull(ontologies);
        this.reachable = checkNotNull(reachable);
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
        Map<OWLOntologyID, Set<OWLOntologyID>> directImports = new LinkedHashMap<>();
        ImmutableSet.Builder<OWLOntologyID> ontologies = ImmutableSet.builder();
        // getImportsClosure() includes the ontology itself and visits each import once, so a
        // cycle between two ontologies ends rather than repeating.
        for (OWLOntology importOntology : ontology.getImportsClosure()) {
            OWLOntologyID importOntologyId = importOntology.getOntologyID();
            ontologies.add(importOntologyId);
            for (OWLDeclarationAxiom declaration : importOntology.getAxioms(AxiomType.DECLARATION)) {
                record(declaredBy, declaration.getEntity(), importOntologyId);
            }
            for (OWLEntity entity : importOntology.getSignature(Imports.EXCLUDED)) {
                record(usedBy, entity, importOntologyId);
            }
            Set<OWLOntologyID> imported = directImports.computeIfAbsent(importOntologyId, key -> new HashSet<>());
            for (OWLOntology directImport : importOntology.getDirectImports()) {
                imported.add(directImport.getOntologyID());
            }
        }
        return new DeclarationIndex(build(declaredBy), build(usedBy), ontologies.build(),
                reachabilityOf(directImports));
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
     * Walks the import edges once per ontology to get everything each one reaches.
     *
     * <p>An ontology in an import cycle is reached from itself, which is what makes two ontologies
     * in a cycle reach each other rather than one of them counting as the earlier.
     */
    private static ImmutableMap<OWLOntologyID, ImmutableSet<OWLOntologyID>> reachabilityOf(
            Map<OWLOntologyID, Set<OWLOntologyID>> directImports) {
        ImmutableMap.Builder<OWLOntologyID, ImmutableSet<OWLOntologyID>> built = ImmutableMap.builder();
        for (Map.Entry<OWLOntologyID, Set<OWLOntologyID>> start : directImports.entrySet()) {
            Set<OWLOntologyID> reached = new HashSet<>();
            Deque<OWLOntologyID> pending = new ArrayDeque<>(start.getValue());
            while (!pending.isEmpty()) {
                OWLOntologyID next = pending.pop();
                if (reached.add(next)) {
                    pending.addAll(directImports.getOrDefault(next, ImmutableSet.of()));
                }
            }
            built.put(start.getKey(), ImmutableSet.copyOf(reached));
        }
        return built.build();
    }

    /**
     * Gets the ontology identifiers of the indexed import closure.
     *
     * <p>An identifier is retained for every ontology in the closure, including one that declares
     * nothing, because a check may need to know that an ontology was loaded and stayed silent.
     *
     * @return the ontology identifiers of the import closure, including that of the ontology the
     *         index was built over
     */
    @Override
    @Nonnull
    public ImmutableSet<OWLOntologyID> getOntologies() {
        return ontologies;
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

    @Override
    @Nonnull
    public ImmutableSet<OWLOntologyID> getMentioningOntologies(@Nonnull OWLEntity entity) {
        checkNotNull(entity);
        return usedBy.getOrDefault(entity, ImmutableSet.of());
    }

    @Override
    public boolean reaches(@Nonnull OWLOntologyID from, @Nonnull OWLOntologyID to) {
        checkNotNull(from);
        checkNotNull(to);
        return reachable.getOrDefault(from, ImmutableSet.of()).contains(to);
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
