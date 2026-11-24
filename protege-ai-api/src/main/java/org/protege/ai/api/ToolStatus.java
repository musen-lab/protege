package org.protege.ai.api;

/** Outcomes shared by Java tool execution and JSON calls. */
public enum ToolStatus {
    SUCCESS, NO_SUCH_TOOL, INVALID_INPUT, TOOL_ERROR, EXECUTION_ERROR, INTERRUPTED, REGISTRY_CLOSED
}
