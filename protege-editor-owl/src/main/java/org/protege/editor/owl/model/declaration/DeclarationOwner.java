package org.protege.editor.owl.model.declaration;

import com.google.auto.value.AutoValue;
import org.semanticweb.owlapi.model.OWLOntologyID;

import javax.annotation.Nonnull;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Identifies the ontology that owns an entity ID and the rule that found it.
 *
 * <p>The rule is kept rather than only its identifier, because the rule also decides which other
 * documents stand in for the owner it chose.
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

    /**
     * Gets the identifier of the rule that found the owning ontology.
     *
     * @return the ownership rule identifier
     */
    @Nonnull
    String getRuleId() {
        return getRule().getId();
    }

    /**
     * Checks whether a document that declares the entity stands in for this owner.
     *
     * @param candidate the ontology that declares the entity
     * @return {@code true} if the candidate stands in for the owner
     */
    boolean isStoodInForBy(@Nonnull OWLOntologyID candidate) {
        return getRule().standsInForOwner(getOntology(), checkNotNull(candidate));
    }
}
