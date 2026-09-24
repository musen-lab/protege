package org.protege.editor.owl.model.io.jsonld;

/**
 * Strategy for serializing rdf:List structures (e.g. owl:unionOf, owl:intersectionOf, owl:oneOf).
 */
public enum RdfListHandling {
    NATIVE_LIST("Raw rdf:first / rdf:rest triple chains"),
    LIST_CONTAINER("JSON Arrays via @container: @list");

    private final String description;

    RdfListHandling(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return description;
    }
}
