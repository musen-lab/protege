package org.protege.ai.core;

import org.protege.ai.api.ToolFailure.Detail;

import java.util.List;

/** Input preparation failed before invoking the tool. */
final class InvalidToolArgumentsException extends Exception {
    private final List<Detail> details;

    InvalidToolArgumentsException(String message) {
        this(message, List.of());
    }

    InvalidToolArgumentsException(String message, List<Detail> details) {
        super(message);
        this.details = List.copyOf(details);
    }

    List<Detail> details() {
        return details;
    }
}
