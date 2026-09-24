package org.protege.editor.owl.model.io.jsonld;

/**
 * Strategy for inlining anonymous blank nodes (such as OWL class restrictions) under properties.
 */
public enum BlankNodeEmbedding {
    EMBED_ONCE("Embed single-reference blank nodes inline"),
    EMBED_ALWAYS("Always embed (duplicate multi-referenced nodes)"),
    REFERENCE("Never embed (Emit top-level referenced blank nodes)");

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
