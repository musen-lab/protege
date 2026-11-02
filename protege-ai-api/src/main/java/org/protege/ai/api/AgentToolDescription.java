package org.protege.ai.api;

/** Metadata available without constructing a tool. inputSchema is a JSON Schema document. */
public record AgentToolDescription(String name, String description, String inputSchema, String resultFormat) {

    public AgentToolDescription {
        requireText(name, "name");
        requireText(description, "description");
        requireText(inputSchema, "inputSchema");
        requireText(resultFormat, "resultFormat");
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Tool " + field + " must not be blank");
        }
    }
}
