package org.protege.editor.owl.model.io.jsonld;

/**
 * Strategy for generating blank node identifiers in the output.
 */
public enum BlankNodeLabeling {
    PRESERVE("Preserve internal parser blank node IDs"),
    GENERATE("Generate sequential blank node IDs (_:b0, _:b1, ...)"),
    DETERMINISTIC("Deterministic canonicalization (Diff-friendly)"),
    SKOLEMIZE("Skolemize to well-known IRIs (/.well-known/genid/...)");

    private final String description;

    BlankNodeLabeling(String description) {
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
