package org.protege.editor.owl.model.io.jsonld;

/**
 * Whether IRI-valued properties receive {@code @type: @id} in the context, allowing their values
 * to appear as compact IRI strings.
 */
public enum TypeCoercionMode {
    /** Keep link values as JSON-LD objects containing {@code @id}. */
    NONE("Off: links are written as {\"@id\": ...}"),
    /** Write links as IRI strings under terms with {@code @type: @id}. */
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
