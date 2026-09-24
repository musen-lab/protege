package org.protege.editor.owl.model.io.jsonld;

/**
 * How the @context definition is embedded in the serialized JSON-LD document.
 */
public enum ContextEmbedding {
    INLINE("Inline embedded object"),
    EXTERNAL_REF("External URI reference"),
    HYBRID("Hybrid (External reference + local overrides)"),
    NONE("Omit @context");

    private final String description;

    ContextEmbedding(String description) {
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
