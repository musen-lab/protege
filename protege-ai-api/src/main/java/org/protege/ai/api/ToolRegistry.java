package org.protege.ai.api;

import java.util.List;
import java.util.Optional;

/** The discovery and invocation boundary used by agents and adapters. */
public interface ToolRegistry {

    List<AgentToolDefinition<?, ?>> listTools();

    Optional<AgentToolDefinition<?, ?>> findTool(String name);

    /**
     * Accepts an already-constructed Java argument object and returns its Java result or a shared failure.
     * Checks the declared Java types; schema validation and defaults belong to the calling service.
     * Treat successful values and their nested contents as read-only; copy before modifying them.
     * Runs on the caller's thread. Concurrent calls are allowed; scheduling belongs to the caller.
     */
    ToolExecutionResult execute(String name, Object arguments);
}
