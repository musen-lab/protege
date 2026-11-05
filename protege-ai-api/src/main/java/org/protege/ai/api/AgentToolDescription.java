package org.protege.ai.api;

/** inputSchema is JSON Schema; resultDescription explains the output's encoding, fields and meaning. */
public record AgentToolDescription(String name, String description, String inputSchema, String resultDescription) {

    public AgentToolDescription {
        requireText(name, "name");
        requireText(description, "description");
        requireText(inputSchema, "inputSchema");
        requireText(resultDescription, "resultDescription");
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Tool " + field + " must not be blank");
        }
    }
}
