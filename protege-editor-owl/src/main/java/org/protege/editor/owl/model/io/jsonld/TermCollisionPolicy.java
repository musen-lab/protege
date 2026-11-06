package org.protege.editor.owl.model.io.jsonld;

/**
 * Resolves collisions when distinct entity IRIs want the same short context term.
 */
public enum TermCollisionPolicy {
    /** Give neither entity the ambiguous short term; use their prefixed or full IRIs. */
    PREFIXED_IRI("Fall back to prefixed IRI (prefix:localName)"),
    /** Give the term to the first IRI in sorted order and fall back for the others. */
    FIRST_WINS("First encountered wins, subsequent fall back to prefixed IRI"),
    /** Append numeric suffixes to later colliding terms. */
    DISAMBIGUATE("Disambiguate with numeric suffix (e.g. name_1, name_2)"),
    /** Reject the save with a storage error. */
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
