package org.protege.editor.owl.model.declaration;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import org.semanticweb.owlapi.model.EntityType;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLOntology;

import javax.annotation.Nonnull;
import java.util.Collection;

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
     * @param referringOntologies the ontologies that use the entity
     * @return a finding containing the entity, its severity, and the ontologies in which it is used
     */
    @Nonnull
    public static MissingDeclarationFinding get(@Nonnull OWLEntity entity,
                                                @Nonnull Collection<OWLOntology> referringOntologies) {
        checkNotNull(entity);
        checkNotNull(referringOntologies);
        return new AutoValue_MissingDeclarationFinding(entity,
                entity.getEntityType(),
                DeclarationSeverity.of(entity),
                ImmutableSet.copyOf(referringOntologies));
    }

    /**
     * Gets the undeclared entity.
     *
     * @return the undeclared entity
     */
    @Nonnull
    public abstract OWLEntity getEntity();

    /**
     * Gets the type of the undeclared entity.
     *
     * @return the entity type
     */
    @Nonnull
    public abstract EntityType<?> getEntityType();

    /**
     * Gets the severity of the missing declaration.
     *
     * @return the severity of the missing declaration
     */
    @Nonnull
    public abstract DeclarationSeverity getSeverity();

    /**
     * Gets the ontologies in which the undeclared entity is used.
     *
     * @return the ontologies that use the entity
     */
    @Nonnull
    public abstract ImmutableSet<OWLOntology> getReferringOntologies();
}
