package org.protege.editor.owl.model.io.jsonld;

/**
 * Controls whether anonymous RDF nodes, such as OWL restrictions, are nested in JSON-LD output.
 */
public enum BlankNodeEmbedding {
    /** Inline a blank node when it has a single reference and doing so would not create a cycle. */
    EMBED_ONCE("Put inside the thing that uses them"),
    /** Keep blank nodes as separate graph nodes with identifier references. */
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
