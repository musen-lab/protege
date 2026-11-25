package org.protege.ai.api;

import java.util.Optional;

/** Schemas and JSON invocation for the tools in one registry. */
public interface JsonToolService {

    /** Returns cached input and output JSON Schemas (draft 2020-12), or empty if the tool is unavailable. */
    Optional<AgentToolSchemas> findSchemas(String name);

    /**
     * Applies defaults, validates input, runs the tool, and validates its serialized result.
     * Success retains both the original Java result and JSON. Failures use the shared ToolFailure type.
     * Runs on the caller's thread; keep expensive calls off Swing's thread.
     */
    AgentToolResult execute(String name, String inputJson);
}
