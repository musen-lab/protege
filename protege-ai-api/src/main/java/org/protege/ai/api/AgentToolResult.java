package org.protege.ai.api;

import java.util.Objects;

/** An invocation's successful value and JSON, or its failure status and message. */
public sealed interface AgentToolResult permits AgentToolResult.Success, AgentToolResult.Failure {

    enum Status {
        SUCCESS, NO_SUCH_TOOL, INVALID_INPUT, EXECUTION_ERROR, REGISTRY_CLOSED
    }

    Status status();

    /** Serialized JSON on success, or an error message on failure. */
    String content();

    default boolean isSuccess() {
        return status() == Status.SUCCESS;
    }

    /**
     * Retains the original result reference and its JSON snapshot, validated by the registry.
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
        public Status status() {
            return Status.SUCCESS;
        }

        @Override
        public String content() {
            return json;
        }
    }

    /** A failure has no successful value or result JSON. */
    record Failure(Status status, String content) implements AgentToolResult {
        public Failure {
            Objects.requireNonNull(status, "Failure status must not be null");
            Objects.requireNonNull(content, "Failure message must not be null");
            if (status == Status.SUCCESS) {
                throw new IllegalArgumentException("Failure cannot have SUCCESS status");
            }
        }
    }
}
