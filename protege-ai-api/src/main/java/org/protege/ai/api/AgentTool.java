package org.protege.ai.api;

import org.protege.editor.core.plugin.ProtegePluginInstance;

/** A tool instance owned by one ontology window. */
public interface AgentTool<I, O> extends ProtegePluginInstance {

    /** Returns stable metadata equal to the factory's definition, without accessing an ontology. */
    AgentToolDefinition<I, O> getDefinition();

    default String getName() {
        return getDefinition().name();
    }

    default String getDescription() {
        return getDefinition().description();
    }

    default Class<I> getInputType() {
        return getDefinition().argumentType();
    }

    /**
     * Runs synchronously and may be called concurrently, including on the same instance.
     * Receives the declared argument type and returns the declared successful result type.
     * Return a completed result and never mutate it or its nested values afterward,
     * including during disposal. Prefer records with immutable contents and unmodifiable collections.
     * Use {@link AgentToolException} for expected failures with a message intended for the caller.
     * Other exceptions are logged and produce a generic error with a diagnostic reference.
     */
    O execute(I arguments) throws Exception;

    @Override
    default void initialise() throws Exception {
    }

    /** Releases resources promptly, after all invocations on this instance have returned. */
    @Override
    default void dispose() throws Exception {
    }
}
