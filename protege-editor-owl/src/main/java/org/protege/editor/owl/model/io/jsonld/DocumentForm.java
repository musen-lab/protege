package org.protege.editor.owl.model.io.jsonld;

/**
 * Selects the JSON-LD document shape written after RDF conversion.
 */
public enum DocumentForm {
    /** Use a context to shorten IRIs and emit graph nodes under {@code @graph}. */
    COMPACTED("Compacted (Human-readable with @context)"),
    /** Emit expanded JSON-LD with full IRIs and no context. */
    EXPANDED("Expanded (W3C full IRIs, no @context)"),
    /** Emit a flat graph of nodes with a context. */
    FLATTENED("Flattened (Normalized flat node array)");

    private final String description;

    DocumentForm(String description) {
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
