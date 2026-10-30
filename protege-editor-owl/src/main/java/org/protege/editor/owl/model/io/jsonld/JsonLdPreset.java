package org.protege.editor.owl.model.io.jsonld;

/**
 * Named groups of JSON-LD save settings. Applying a preset replaces the current setting values;
 * {@link #CUSTOM} labels user-edited values without replacing them.
 */
public enum JsonLdPreset {
    /** Readable compact output with short terms, language maps, and nested restrictions. */
    WEB_FRIENDLY("Web-friendly (Default)", "Easiest to read: short names, language maps and nested restrictions"),
    /** Preserve RDF details in a form intended for reliable reloads. */
    ROUND_TRIP_SAFE("Round-trip safe", "Closest to the raw RDF; every value keeps its exact spelling"),
    /** Favor stable, compact diffs for version control. */
    DIFF_FRIENDLY("Diff-friendly", "Small, stable changes in version control"),
    /** Stream full IRIs and typed literals with separate blank nodes and RDF list links. */
    EXPANDED("Expanded (low memory)", "Full IRIs, exact typed literals, separate blank nodes and RDF list links"),
    /** Keep values chosen individually in the preferences panel. */
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
