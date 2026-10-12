package org.protege.editor.owl.model.io.jsonld;

/**
 * Presets that set all JSON-LD save settings at once.
 */
public enum JsonLdPreset {
    WEB_FRIENDLY("Web-friendly (Default)", "Easiest to read: short names, language maps and nested restrictions"),
    ROUND_TRIP_SAFE("Round-trip safe", "Closest to the raw RDF; every value keeps its exact spelling"),
    DIFF_FRIENDLY("Diff-friendly", "Small, stable changes in version control"),
    EXPANDED("Expanded (Debug)", "Full IRIs and no @context"),
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
