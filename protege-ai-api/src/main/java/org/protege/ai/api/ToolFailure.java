package org.protege.ai.api;

import java.util.List;
import java.util.Objects;

/** The same failure can be returned by Java execution and JSON calls, without a success value. */
public record ToolFailure(ToolStatus status, String content, String code, List<Detail> details)
        implements ToolExecutionResult, AgentToolResult {

    public ToolFailure(ToolStatus status, String content) {
        this(status, content, Objects.requireNonNull(status).name(), List.of());
    }

    public ToolFailure {
        Objects.requireNonNull(status, "Failure status must not be null");
        Objects.requireNonNull(content, "Failure message must not be null");
        Objects.requireNonNull(code, "Failure code must not be null");
        if (code.isBlank()) {
            throw new IllegalArgumentException("Failure code must not be blank");
        }
        details = List.copyOf(details);
        if (status == ToolStatus.SUCCESS) {
            throw new IllegalArgumentException("Failure cannot have SUCCESS status");
        }
    }

    @Override
    public boolean isSuccess() {
        return false;
    }

    /** An input location and explanation. JSON calls use JSON Pointers, with an empty string for the root. */
    public record Detail(String field, String message) {
        public Detail {
            Objects.requireNonNull(field, "Error field must not be null");
            Objects.requireNonNull(message, "Error message must not be null");
        }
    }
}
