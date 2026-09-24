package org.protege.editor.owl.model.io.jsonld;

/**
 * High-level serialization presets for quickly configuring the 25 JSON-LD parameters.
 */
public enum JsonLdPreset {
    WEB_FRIENDLY("Web-friendly (Default)", "Human-readable compacted JSON-LD optimized for Web and API developers"),
    ROUND_TRIP_SAFE("Round-trip safe", "Lossless representation preserving all explicit datatypes and RDF lists"),
    DIFF_FRIENDLY("Diff-friendly", "Deterministic key ordering and canonical blank nodes for Git version control"),
    EXPANDED("Expanded (Debug)", "W3C expanded RDF-in-JSON format with full IRIs"),
    CUSTOM("Custom", "Customized configuration");

    private final String label;
    private final String description;

    JsonLdPreset(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return label;
    }
}
