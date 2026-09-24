package org.protege.editor.owl.model.io.jsonld;

/**
 * Strategy for determining the origin of the JSON-LD @context.
 */
public enum ContextSource {
    GENERATED("Generated from ontology signature & prefixes"),
    USER_SUPPLIED("User-supplied external context"),
    MERGED("Merged (User-supplied overlaid on generated)");

    private final String description;

    ContextSource(String description) {
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
