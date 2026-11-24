package org.protege.ai.api;

import java.util.Objects;

/** A tool's Java result or a shared failure, without a serialized representation. */
public sealed interface ToolExecutionResult permits ToolExecutionResult.Success, ToolFailure {

    ToolStatus status();

    default boolean isSuccess() {
        return status() == ToolStatus.SUCCESS;
    }

    /** Retains the original result. Producers and consumers must treat it and its contents as read-only. */
    record Success<R>(R value) implements ToolExecutionResult {
        public Success {
            Objects.requireNonNull(value, "Tool result must not be null");
        }

        @Override
        public ToolStatus status() {
            return ToolStatus.SUCCESS;
        }
    }
}
