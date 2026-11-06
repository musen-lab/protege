package org.protege.editor.owl.model.io.jsonld;

/**
 * Source of short entity names in the generated {@code @context}.
 */
public enum TermNameSource {
    /** Use the final component of the entity IRI. */
    LOCAL_NAME("IRI local name"),
    /** Prefer an English or untagged {@code rdfs:label}, falling back to the IRI local name. */
    RDFS_LABEL("rdfs:label (prefers English or untagged labels)");

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
