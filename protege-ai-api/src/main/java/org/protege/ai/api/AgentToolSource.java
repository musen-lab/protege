package org.protege.ai.api;

/** Supplies a definition and a fresh instance. Tests can supply this without a plugin runtime. */
public interface AgentToolSource<I, O> {

    String getId();

    AgentToolDefinition<I, O> getDefinition();

    AgentTool<I, O> newInstance() throws Exception;
}
