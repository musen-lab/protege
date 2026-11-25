package org.protege.ai.api;

import java.util.List;
import java.util.Objects;

/** An expected tool failure. Its message and details are deliberately safe to return to the caller. */
public final class AgentToolException extends Exception {

    private final String code;
    private final List<ToolFailure.Detail> details;

    public AgentToolException(String code, String message) {
        this(code, message, List.of());
    }

    public AgentToolException(String code, String message, List<ToolFailure.Detail> details) {
        super(Objects.requireNonNull(message));
        this.code = Objects.requireNonNull(code);
        if (code.isBlank() || message.isBlank()) {
            throw new IllegalArgumentException("Tool error code and message must not be blank");
        }
        this.details = List.copyOf(details);
    }

    public String code() {
        return code;
    }

    public List<ToolFailure.Detail> details() {
        return details;
    }
}
