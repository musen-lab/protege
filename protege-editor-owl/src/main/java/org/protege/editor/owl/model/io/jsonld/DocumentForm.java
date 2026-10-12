package org.protege.editor.owl.model.io.jsonld;

/**
 * High-level JSON-LD document representation form.
 */
public enum DocumentForm {
    COMPACTED("Compacted (Human-readable with @context)"),
    EXPANDED("Expanded (W3C full IRIs, no @context)"),
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
