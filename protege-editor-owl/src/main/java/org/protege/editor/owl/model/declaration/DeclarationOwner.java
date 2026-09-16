package org.protege.editor.owl.model.declaration;

import com.google.auto.value.AutoValue;
import org.semanticweb.owlapi.model.OWLOntologyID;

import javax.annotation.Nonnull;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Identifies the ontology that owns an entity ID and the rule that found it.
 *
 * @author Josef Hardi
 */
@AutoValue
abstract class DeclarationOwner {

    /**
     * Creates an entity ID owner.
     *
     * @param ontology the ontology that owns the entity ID
     * @param rule the rule that found the ontology
     * @return the entity ID owner
     */
    @Nonnull
    static DeclarationOwner get(@Nonnull OWLOntologyID ontology, @Nonnull OwnershipRule rule) {
        return new AutoValue_DeclarationOwner(checkNotNull(ontology), checkNotNull(rule));
    }

    /**
     * Gets the ontology that owns the entity ID.
     *
     * @return the owning ontology
     */
    @Nonnull
    abstract OWLOntologyID getOntology();

    /**
     * Gets the rule that found the owning ontology.
     *
     * @return the ownership rule
     */
    @Nonnull
    abstract OwnershipRule getRule();
}
