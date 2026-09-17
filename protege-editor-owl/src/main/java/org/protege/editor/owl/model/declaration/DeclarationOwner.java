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
     * @param ruleId the identifier of the rule that made the finding
     * @return the entity ID owner
     */
    @Nonnull
    static DeclarationOwner get(@Nonnull OWLOntologyID ontology, @Nonnull String ruleId) {
        return new AutoValue_DeclarationOwner(checkNotNull(ontology), checkNotNull(ruleId));
    }

    /**
     * Gets the ontology that owns the entity ID.
     *
     * @return the owning ontology
     */
    @Nonnull
    abstract OWLOntologyID getOntology();

    /**
     * Gets the identifier of the rule that made the finding.
     *
     * @return the ownership rule identifier
     */
    @Nonnull
    abstract String getRuleId();
}
