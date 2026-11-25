package org.protege.ai.core;

import org.protege.ai.api.AgentToolDefinition;
import org.protege.ai.api.AgentToolResult;
import org.protege.ai.api.AgentToolSchemas;
import org.protege.ai.api.AgentToolSource;
import org.protege.ai.api.JsonToolService;
import org.protege.ai.api.ToolExecutionResult;
import org.protege.ai.api.ToolFailure;
import org.protege.editor.core.Disposable;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLDataFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.protege.ai.api.ToolStatus.INVALID_INPUT;

/** Owns JSON contracts for one registry, using the registry's managed execution path. */
public final class AgentToolJsonService implements JsonToolService, Disposable {
    private final Map<String, ToolJsonCodec<?, ?>> codecs = new ConcurrentHashMap<>();
    private final OWLDataFactory dataFactory;
    private final AgentToolRegistry registry;

    public AgentToolJsonService(Iterable<? extends AgentToolSource<?, ?>> sources) {
        this(sources, OWLManager.getOWLDataFactory());
    }

    public AgentToolJsonService(Iterable<? extends AgentToolSource<?, ?>> sources, OWLDataFactory dataFactory) {
        this.dataFactory = Objects.requireNonNull(dataFactory);
        registry = new AgentToolRegistry(List.of(), this::prepare);
        sources.forEach(registry::register);
    }

    public AgentToolRegistry registry() {
        return registry;
    }

    private void prepare(AgentToolDefinition<?, ?> definition) {
        // Check and cache the contract before the registry constructs the tool.
        codecs.put(definition.name(), new ToolJsonCodec<>(definition, dataFactory));
    }

    @Override
    public Optional<AgentToolSchemas> findSchemas(String name) {
        return registry.findTool(name).map(definition -> codecs.get(definition.name()).schemas());
    }

    @Override
    public AgentToolResult execute(String name, String inputJson) {
        if (inputJson == null) {
            return new ToolFailure(INVALID_INPUT, "Tool input must not be null");
        }
        ToolExecutionResult result = registry.execute(name,
                definition -> codecs.get(definition.name()).readArguments(inputJson),
                (definition, value) -> new AgentToolResult.Success<>(value,
                        codecs.get(definition.name()).writeResult(value)));
        if (result instanceof ToolFailure failure) {
            return failure;
        }
        return (AgentToolResult) ((ToolExecutionResult.Success<?>) result).value();
    }

    @Override
    public void dispose() {
        registry.dispose();
        // Running calls still need their codecs. They are released with this service, not during window closure.
    }
}
