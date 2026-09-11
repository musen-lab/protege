package org.protege.editor.owl.model.declaration;

import org.semanticweb.owlapi.model.OWLEntity;

import javax.annotation.Nonnull;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Provides a check to entities that belong to standard OWL and related vocabularies, such as
 * {@code owl:Thing} and {@code xsd:integer}.
 *
 * <p>Entities in these vocabularies do not require explicit declarations and are therefore
 * excluded when checking for missing declarations.
 *
 * @author Josef Hardi
 */
public final class StandardVocabulary {

    private StandardVocabulary() {
    }

    /**
     * Determines whether the specified entity belongs to a standard vocabulary and does not
     * require an explicit declaration.
     *
     * <p>An entity is considered standard if it is recognized by the OWL API as a built-in entity
     * or if its IRI belongs to a reserved vocabulary namespace. Checking both cases also covers
     * reserved vocabulary terms that are not recognized as built-in entities.
     *
     * @param entity the entity to check
     * @return {@code true} if the entity belongs to a standard vocabulary and does not require
     *         an explicit declaration, otherwise {@code false}
     */
    public static boolean contains(@Nonnull OWLEntity entity) {
        checkNotNull(entity);
        return entity.isBuiltIn() || entity.getIRI().isReservedVocabulary();
    }
}
