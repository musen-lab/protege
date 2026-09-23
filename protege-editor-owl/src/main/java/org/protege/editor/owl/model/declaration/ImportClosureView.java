package org.protege.editor.owl.model.declaration;

import com.google.common.collect.ImmutableSet;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLOntologyID;

import javax.annotation.Nonnull;

/**
 * Supplies an {@link OwnershipRule} with information about the import closure currently being
 * checked.
 *
 * <p>The import closure consists of the root ontology and every ontology it imports, directly or
 * indirectly. Before entities are checked, each ownership rule receives a view of that closure and
 * uses it to select the ontology that should own an entity. The rule can find out:
 *
 * <ul>
 *   <li>which ontologies are in the closure,</li>
 *   <li>which of those ontologies mention a particular entity, and</li>
 *   <li>whether one ontology imports another, directly or indirectly.</li>
 * </ul>
 *
 * <p>For example, if {@code application.owl} imports {@code core.owl} and both mention the same
 * entity, the view reports both mentions and the path from {@code application.owl} to
 * {@code core.owl}. A rule can use those facts to select {@code core.owl} as the owner.
 *
 * <p>This is a read-only summary, not access to the ontology documents themselves. It records
 * mentions rather than declarations: any axiom containing an entity counts as a mention. The
 * declaration check separately decides whether the entity is declared and whether that
 * declaration is misplaced.
 *
 * @author Josef Hardi
 */
public interface ImportClosureView {

    /**
     * Returns the identifiers of every ontology in the import closure.
     *
     * @return the ontology identifiers, including the root ontology and all of its transitive
     *         imports
     */
    @Nonnull
    ImmutableSet<OWLOntologyID> getOntologies();

    /**
     * Returns the ontologies in this closure that use an entity in their own axioms.
     *
     * <p>This tells an ownership rule where the entity actually occurs. For example, suppose
     * {@code application.owl} imports {@code core.owl}, and an axiom in {@code core.owl} uses
     * {@code Customer}. The result contains {@code core.owl}, but not {@code application.owl}
     * merely because it imports {@code core.owl}. If an axiom in {@code application.owl} also uses
     * {@code Customer}, the result contains both ontologies.
     *
     * <p>Any occurrence in an ontology's own signature counts as a mention. This includes a
     * declaration axiom, but an entity does not have to be declared to be mentioned. Imported
     * signatures are excluded.
     *
     * @param entity the entity to look up
     * @return the ontology identifiers that mention {@code entity}, or an empty set if the entity
     *         does not occur in the closure
     */
    @Nonnull
    ImmutableSet<OWLOntologyID> getMentioningOntologies(@Nonnull OWLEntity entity);

    /**
     * Tests whether one ontology reaches another by following one or more import edges.
     *
     * <p>Reachability is directed and transitive. If {@code A} imports {@code B}, then
     * {@code reaches(A, B)} is true, but {@code reaches(B, A)} is false unless another import path
     * leads back to {@code A}. Consequently, an ontology reaches itself only when it participates
     * in an import cycle.
     *
     * @param from the ontology at which to start following imports
     * @param to the ontology to find
     * @return {@code true} if a non-empty import path leads from {@code from} to {@code to},
     *         otherwise {@code false}
     */
    boolean reaches(@Nonnull OWLOntologyID from, @Nonnull OWLOntologyID to);
}
