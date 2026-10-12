package org.protege.editor.owl.model.io.jsonld;

/**
 * Whether object properties get "@type": "@id" in @context, so their values can be written as plain IRI strings.
 */
public enum TypeCoercionMode {
    NONE("Off: links are written as {\"@id\": ...}"),
    ID("On: object properties and OWL links are plain IRI strings");

    private final String description;

    TypeCoercionMode(String description) {
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
