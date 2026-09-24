package org.protege.editor.owl.model.io.jsonld;

/**
 * Strategy for ordering object keys in serialized JSON-LD output.
 */
public enum KeyOrdering {
    NONE("Natural / Unspecified order"),
    LEXICOGRAPHIC("Strict alphabetical key order"),
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
