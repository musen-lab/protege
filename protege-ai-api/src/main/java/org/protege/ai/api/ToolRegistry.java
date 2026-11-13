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
     * Accepts JSON arguments and returns JSON content on success.
     * Runs on the caller's thread. Concurrent calls are allowed; scheduling belongs to the caller.
     */
    AgentToolResult execute(String name, String input);
}
