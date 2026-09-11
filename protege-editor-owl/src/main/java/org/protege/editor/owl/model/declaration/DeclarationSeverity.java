package org.protege.editor.owl.model.declaration;

import org.semanticweb.owlapi.model.EntityType;
import org.semanticweb.owlapi.model.OWLEntity;

import javax.annotation.Nonnull;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Indicates the severity of a missing entity declaration.
 *
 * @author Josef Hardi
 */
public enum DeclarationSeverity {

    /**
     * Indicates that the missing declaration should be treated as an error.
     */
    ERROR,

    /**
     * Indicates that the missing declaration should be treated as a warning.
     */
    WARNING;

    /**
     * Determines the severity of a missing declaration for the specified entity.
     *
     * <p>A missing declaration is reported as a warning for named individuals and for entities
     * from third-party vocabularies. All other missing declarations are reported as errors.
     *
     * @param entity the entity for which a declaration is missing
     * @return the severity of the missing declaration
     */
    @Nonnull
    public static DeclarationSeverity of(@Nonnull OWLEntity entity) {
        checkNotNull(entity);
        if (entity.getEntityType() == EntityType.NAMED_INDIVIDUAL) {
            return WARNING;
        }
        return ThirdPartyVocabulary.contains(entity) ? WARNING : ERROR;
    }
}
