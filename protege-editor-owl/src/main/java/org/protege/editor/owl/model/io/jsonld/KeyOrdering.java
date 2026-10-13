package org.protege.editor.owl.model.io.jsonld;

/**
 * Ordering applied to JSON object keys after JSON-LD shaping.
 */
public enum KeyOrdering {
    /** Keep the order produced by the JSON-LD processing library. */
    NONE("Natural / Unspecified order"),
    /** Sort all object keys lexicographically. */
    LEXICOGRAPHIC("Strict alphabetical key order"),
    /** Place key JSON-LD keywords first, then sort other keys. */
    KEYWORDS_FIRST("JSON-LD keywords first (@context, @id, @type) then sorted");

    private final String description;

    KeyOrdering(String description) {
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
