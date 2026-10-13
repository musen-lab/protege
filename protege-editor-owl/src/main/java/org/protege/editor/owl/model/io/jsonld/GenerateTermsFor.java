package org.protege.editor.owl.model.io.jsonld;

/**
 * Entity categories that receive short term mappings in the generated {@code @context}.
 */
public enum GenerateTermsFor {
    /** Do not create optional short terms; coercion and language maps may still need definitions. */
    NONE("None (Prefixes only)"),
    /** Create short terms for annotation properties only. */
    ANNOTATION_PROPERTIES("Annotation properties only"),
    /** Create short terms for annotation, object, and data properties. */
    ALL_PROPERTIES("All properties (Annotation, Object, Data)"),
    /** Create short terms for properties and classes. */
    ALL_ENTITIES("All properties and classes");

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
