package org.protege.editor.owl.model.io.jsonld;

/**
 * Entity categories that receive short term mappings in the generated @context.
 */
public enum GenerateTermsFor {
    NONE("None (Prefixes only)"),
    ANNOTATION_PROPERTIES("Annotation properties only"),
    ALL_PROPERTIES("All properties (Annotation, Object, Data)"),
    ALL_ENTITIES("All entities (Properties + Classes + Individuals)");

    private final String description;

    GenerateTermsFor(String description) {
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
