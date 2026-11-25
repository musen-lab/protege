package org.protege.ai.api;

import java.util.Objects;

/** An invocation's successful value and JSON, or its failure status and message. */
public sealed interface AgentToolResult permits AgentToolResult.Success, ToolFailure {

    ToolStatus status();

    /** Serialized JSON on success, or an error message on failure. */
    String content();

    default boolean isSuccess() {
        return status() == ToolStatus.SUCCESS;
    }

    /**
     * Retains the original result reference and its JSON snapshot, validated by the JSON service.
     * Producers must not mutate the result after returning it, including during disposal.
     * Consumers must treat it and its nested values as read-only, copying before making changes.
     * No defensive copy or deep immutability enforcement is performed.
     */
    record Success<R>(R value, String json) implements AgentToolResult {
        public Success {
            Objects.requireNonNull(value, "Tool result must not be null");
            Objects.requireNonNull(json, "Result JSON must not be null");
        }

        @Override
        public ToolStatus status() {
            return ToolStatus.SUCCESS;
        }

        @Override
        public String content() {
            return json;
        }
    }
}
