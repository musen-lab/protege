package org.protege.editor.owl.model.io.jsonld;

import org.eclipse.rdf4j.rio.helpers.JSONLDMode;

/**
 * High-level JSON-LD document representation form.
 */
public enum DocumentForm {
    COMPACTED("Compacted (Human-readable with @context)", JSONLDMode.COMPACT),
    EXPANDED("Expanded (W3C full IRIs, no @context)", JSONLDMode.EXPAND),
    FLATTENED("Flattened (Normalized flat node array)", JSONLDMode.FLATTEN);

    private final String description;
    private final JSONLDMode rioMode;

    DocumentForm(String description, JSONLDMode rioMode) {
        this.description = description;
        this.rioMode = rioMode;
    }

    public String getDescription() {
        return description;
    }

    public JSONLDMode getRioMode() {
        return rioMode;
    }

    @Override
    public String toString() {
        return description;
    }
}
