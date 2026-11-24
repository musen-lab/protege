package org.protege.ai.api;

import java.util.List;
import java.util.Optional;

/** The discovery and invocation boundary used by agents and adapters. */
public interface ToolRegistry {

    List<AgentToolDefinition<?, ?>> listTools();

    Optional<AgentToolDefinition<?, ?>> findTool(String name);

    /** Returns the registered tool's cached input and output JSON Schemas (draft 2020-12). */
    Optional<AgentToolSchemas> findSchemas(String name);

    /**
     * Accepts JSON arguments and returns a {@link AgentToolResult.Success} with the original
     * result reference and validated JSON, or a {@link ToolFailure}.
     * Treat successful values and their nested contents as read-only; copy before making changes.
     * Selection by name does not establish a compile-time result type; inspect {@code Success<?>}.
     * Runs on the caller's thread. Concurrent calls are allowed; scheduling belongs to the caller.
     */
    AgentToolResult execute(String name, String input);
}
