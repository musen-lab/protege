package org.protege.ai.core;

import org.protege.ai.api.AgentTool;
import org.protege.ai.api.AgentToolDefinition;

/** A tool with a definition and a test-supplied operation. */
record ToolStub<I, O>(AgentToolDefinition<I, O> definition, Invocation<I, O> invocation)
        implements AgentTool<I, O> {

    @FunctionalInterface
    interface Invocation<I, O> {
        O execute(I input) throws Exception;
    }

    @Override
    public AgentToolDefinition<I, O> getDefinition() {
        return definition;
    }

    @Override
    public O execute(I input) throws Exception {
        return invocation.execute(input);
    }
}
