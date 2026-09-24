package org.protege.editor.owl.model.io.jsonld;

/**
 * Strategy used when two distinct IRIs resolve to the same short term name.
 */
public enum TermCollisionPolicy {
    PREFIXED_IRI("Fall back to prefixed IRI (prefix:localName)"),
    FIRST_WINS("First encountered wins, subsequent fall back to prefixed IRI"),
    DISAMBIGUATE("Disambiguate with numeric suffix (e.g. name_1, name_2)"),
    ERROR("Fail with serialization error");

    private final String description;

    TermCollisionPolicy(String description) {
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
