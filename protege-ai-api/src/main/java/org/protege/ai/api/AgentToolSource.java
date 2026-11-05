package org.protege.ai.api;

/** Supplies a description and a fresh instance. Tests can supply this without a plugin runtime. */
public interface AgentToolSource {

    String getId();

    AgentToolDescription getDescription();

    AgentTool newInstance() throws Exception;
}
