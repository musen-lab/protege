package org.protege.editor.owl.model.io.jsonld;

/**
 * Strategy used to extract term names for entities in @context.
 */
public enum TermNameSource {
    LOCAL_NAME("IRI Local Name (Deterministic)"),
    RDFS_LABEL("rdfs:label (Prefers English or default language)");

    private final String description;

    TermNameSource(String description) {
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
