package org.protege.ai.api;

import java.util.Objects;

/** Declares a tool contract without constructing a tool instance. */
public record AgentToolDefinition<I, O>(
        String name,
        String description,
        Class<I> argumentType,
        Class<O> resultType) {

    public AgentToolDefinition {
        requireText(name, "name");
        requireText(description, "description");
        Objects.requireNonNull(argumentType, "Tool argument type must not be null");
        Objects.requireNonNull(resultType, "Tool result type must not be null");
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Tool " + field + " must not be blank");
        }
    }
}
