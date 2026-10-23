package org.protege.editor.owl.model.declaration;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import org.semanticweb.owlapi.model.EntityType;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLOntologyID;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Represents an entity that is used but not declared.
 *
 * @author Josef Hardi
 */
@AutoValue
public abstract class MissingDeclarationFinding {

    /**
     * Creates a finding for an undeclared entity.
     *
     * @param entity the undeclared entity
     * @param mentioningOntologies the ontologies that mention the entity
     * @param placementOntology the ontology the declaration should be placed
     * @return a finding containing the entity, its severity, and the ontologies in which it is used
     */
    @Nonnull
    public static MissingDeclarationFinding create(@Nonnull OWLEntity entity,
                                                   @Nonnull Collection<OWLOntologyID> mentioningOntologies,
                                                   @Nonnull Optional<OWLOntologyID> placementOntology) {
        checkNotNull(entity);
        checkNotNull(mentioningOntologies);
        return new AutoValue_MissingDeclarationFinding(entity,
                DeclarationSeverity.of(entity),
                ImmutableSet.copyOf(mentioningOntologies),
                checkNotNull(placementOntology));
    }

    /**
     * Gets the undeclared entity.
     *
     * @return the undeclared entity
     */
    @Nonnull
    public abstract OWLEntity getEntity();

    /**
     * Gets the severity of the missing declaration.
     *
     * @return the severity of the missing declaration
     */
    @Nonnull
    public abstract DeclarationSeverity getSeverity();

    /**
     * Gets the ontologies in which the undeclared entity is mentioned.
     *
     * @return the ontologies that mention the entity
     */
    @Nonnull
    public abstract ImmutableSet<OWLOntologyID> getMentioningOntologies();

    /**
     * Gets the ontology that the declaration should be placed.
     *
     * @return the ontologyr, or empty if none was determined
     */
    @Nonnull
    public abstract Optional<OWLOntologyID> getPlacementOntology();

    /**
     * Gets the type of the undeclared entity.
     *
     * @return the entity type
     */
    @Nonnull
    public EntityType<?> getEntityType() {
        return getEntity().getEntityType();
    }
}
