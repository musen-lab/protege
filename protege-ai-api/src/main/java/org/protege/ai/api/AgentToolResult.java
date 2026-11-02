package org.protege.ai.api;

import java.util.Objects;

/** Content follows the tool's result description on success, or explains an error. */
public record AgentToolResult(Status status, String content) {

    public enum Status {
        SUCCESS, NO_SUCH_TOOL, INVALID_INPUT, EXECUTION_ERROR, REGISTRY_CLOSED
    }

    public AgentToolResult {
        Objects.requireNonNull(status);
        Objects.requireNonNull(content);
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }
}
