package org.protege.editor.owl.model.declaration;

/**
 * Lists the rules for finding the ontology that owns an entity ID.
 *
 * @author Josef Hardi
 */
public enum OwnershipRule {

    /**
     * Matches the ID space of an OBO ID to an ontology name. For example, {@code GO_0006915}
     * belongs to {@code go.owl}.
     */
    OBO_IDENTIFIER,

    /**
     * Matches the namespace of an entity IRI to an ontology IRI.
     */
    NAMESPACE
}
