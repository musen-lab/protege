package org.protege.editor.owl.model.io.jsonld;

/**
 * Strategy for automatically adding @type coercion to property definitions in @context.
 */
public enum TypeCoercionMode {
    NONE("No automatic coercion"),
    ID("Coerce all object properties to @id"),
    VOCAB("Coerce to @vocab where applicable"),
    AUTO("Smart auto-coercion (Object properties -> @id)");

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
