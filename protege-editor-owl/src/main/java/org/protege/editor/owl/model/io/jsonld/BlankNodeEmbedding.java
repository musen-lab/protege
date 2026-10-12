package org.protege.editor.owl.model.io.jsonld;

/**
 * Strategy for inlining anonymous blank nodes (such as OWL class restrictions) under properties.
 */
public enum BlankNodeEmbedding {
    EMBED_ONCE("Put inside the thing that uses them"),
    REFERENCE("Keep as separate nodes");

    private final String description;

    BlankNodeEmbedding(String description) {
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
