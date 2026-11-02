package org.protege.ai.api;

import java.util.Objects;

/** Content is the declared result format on success, or a human-readable error message. */
public record AgentToolResult(Status status, String content) {

    public enum Status {
        SUCCESS, NO_SUCH_TOOL, EXECUTION_ERROR, REGISTRY_CLOSED
    }

    public AgentToolResult {
        Objects.requireNonNull(status);
        Objects.requireNonNull(content);
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }
}
