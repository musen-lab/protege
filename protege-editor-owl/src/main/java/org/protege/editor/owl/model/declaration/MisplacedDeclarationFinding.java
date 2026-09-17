package org.protege.editor.owl.model.declaration;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import org.semanticweb.owlapi.model.EntityType;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLOntologyID;

import javax.annotation.Nonnull;
import java.util.Collection;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Represents an entity whose declaration sits outside the ontology that owns its identifier.
 *
 * <p>The finding is the result of a heuristic rather than a defect the specification defines, so it
 * always carries {@link DeclarationSeverity#WARNING}. {@link DeclarationSeverity#of(OWLEntity)}
 * answers a different question, which is how serious it is that a declaration is missing
 * altogether.
 *
 * @author Josef Hardi
 */
@AutoValue
public abstract class MisplacedDeclarationFinding {

    /**
     * Creates a finding for an entity declared away from the ontology that owns its identifier.
     *
     * @param entity the entity whose declaration is potentially misplaced
     * @param owningOntology the identifier of the ontology that owns the entity's identifier
     * @param declaringOntologies the identifiers of the ontologies outside the owner's family that
     *                            declare the entity
     * @param ownershipRuleId the identifier of the rule that made the finding
     * @return a finding at warning severity
     */
    @Nonnull
    public static MisplacedDeclarationFinding get(@Nonnull OWLEntity entity,
                                                  @Nonnull OWLOntologyID owningOntology,
                                                  @Nonnull Collection<OWLOntologyID> declaringOntologies,
                                                  @Nonnull String ownershipRuleId) {
        checkNotNull(entity);
        checkNotNull(owningOntology);
        checkNotNull(declaringOntologies);
        checkNotNull(ownershipRuleId);
        return new AutoValue_MisplacedDeclarationFinding(entity,
                DeclarationSeverity.WARNING,
                owningOntology,
                ImmutableSet.copyOf(declaringOntologies),
                ownershipRuleId);
    }

    /**
     * Gets the entity whose declaration is potentially misplaced.
     *
     * @return the entity
     */
    @Nonnull
    public abstract OWLEntity getEntity();

    /**
     * Gets the severity of the finding, which is always a warning.
     *
     * @return the severity of the finding
     */
    @Nonnull
    public abstract DeclarationSeverity getSeverity();

    /**
     * Gets the identifier of the ontology that owns the entity's identifier and does not declare it.
     *
     * @return the owning ontology identifier
     */
    @Nonnull
    public abstract OWLOntologyID getOwningOntology();

    /**
     * Gets the identifiers of the ontologies outside the owner's family that declare the entity.
     *
     * @return the declaring ontology identifiers
     */
    @Nonnull
    public abstract ImmutableSet<OWLOntologyID> getDeclaringOntologies();

    /**
     * Gets the identifier of the rule that made the finding.
     *
     * @return the ownership rule identifier
     */
    @Nonnull
    public abstract String getOwnershipRuleId();

    /**
     * Gets the type of the entity.
     *
     * @return the entity type
     */
    @Nonnull
    public EntityType<?> getEntityType() {
        return getEntity().getEntityType();
    }
}
