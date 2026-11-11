package org.protege.ai.core;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.protege.ai.api.AgentTool;
import org.protege.ai.api.AgentToolDefinition;
import org.protege.ai.api.AgentToolSource;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;
import static org.protege.ai.api.AgentToolResult.Status.*;

public class AgentToolTypedContractTest {

    public enum Scope { DIRECT, ALL }

    public record Arguments(@JsonProperty("label") String name, Scope scope) {}

    public record Match(String label) {}

    public record Result(List<Match> matches) {}

    private static final AgentToolDefinition<Arguments, Result> DEFINITION =
            new AgentToolDefinition<>("find", "Find matching labels", Arguments.class, Result.class);

    private <I, O> AgentToolSource<I, O> source(AgentToolDefinition<I, O> definition, AgentTool<I, O> tool) {
        return new AgentToolSource<>() {
            public String getId() { return definition.name(); }
            public AgentToolDefinition<I, O> getDefinition() { return definition; }
            public AgentTool<I, O> newInstance() { return tool; }
        };
    }

    @Test
    public void passesTypedArgumentsToToolAndSerializesNestedResults() throws Exception {
        var received = new AtomicReference<Arguments>();
        var registry = new AgentToolRegistry(List.of(source(DEFINITION, arguments -> {
            received.set(arguments);
            return new Result(List.of(new Match(arguments.name())));
        })));

        var result = registry.execute("find", "{\"label\":\"Pizza\",\"scope\":\"DIRECT\"}");

        assertEquals(SUCCESS, result.status());
        assertEquals(new Arguments("Pizza", Scope.DIRECT), received.get());
        var mapper = new ObjectMapper();
        assertEquals(mapper.readTree("{\"matches\":[{\"label\":\"Pizza\"}]}"), mapper.readTree(result.content()));
        assertSame(Arguments.class, registry.findTool("find").orElseThrow().argumentType());
        assertSame(Result.class, registry.findTool("find").orElseThrow().resultType());
        registry.dispose();
    }

    @Test
    public void rejectsMalformedOrMismatchedArgumentsBeforeToolExecution() {
        var calls = new AtomicInteger();
        var registry = new AgentToolRegistry(List.of(source(DEFINITION, arguments -> {
            calls.incrementAndGet();
            return new Result(List.of());
        })));
        for (String input : List.of(
                "not json", "null", "{}",
                "{\"label\":\"Pizza\"}",
                "{\"label\":null,\"scope\":\"ALL\"}",
                "{\"label\":\"Pizza\",\"scope\":null}",
                "{\"label\":42,\"scope\":\"ALL\"}",
                "{\"label\":4.2,\"scope\":\"ALL\"}",
                "{\"label\":true,\"scope\":\"ALL\"}",
                "{\"label\":\"Pizza\",\"scope\":\"INDIRECT\"}",
                "{\"label\":\"Pizza\",\"scope\":0}",
                "{\"label\":\"Pizza\",\"scope\":\"ALL\",\"extra\":true}",
                "{\"label\":\"Pizza\",\"scope\":\"ALL\"} {}")) {
            assertEquals(input, INVALID_INPUT, registry.execute("find", input).status());
        }
        assertEquals(0, calls.get());
        assertEquals("{\"matches\":[]}",
                registry.execute("find", "{\"label\":\"Pizza\",\"scope\":\"ALL\"}").content());
        assertEquals(1, calls.get());
        registry.dispose();
    }

    public record CountArguments(int count) {}

    public record CountResult(int count) {}

    @Test
    public void keepsDifferentToolContractsSeparate() {
        var count = new AgentToolDefinition<>("count", "Return a count", CountArguments.class, CountResult.class);
        var registry = new AgentToolRegistry(List.of(
                source(DEFINITION, arguments -> new Result(List.of())),
                source(count, arguments -> new CountResult(arguments.count()))));

        assertEquals("{\"count\":7}", registry.execute("count", "{\"count\":7}").content());
        assertEquals(INVALID_INPUT, registry.execute("count", "{\"count\":\"7\"}").status());
        assertEquals(INVALID_INPUT, registry.execute("count", "{\"count\":7.5}").status());
        assertEquals(INVALID_INPUT, registry.execute("count", "{\"count\":null}").status());
        assertEquals(INVALID_INPUT, registry.execute("find", "{\"count\":7}").status());
        assertEquals(List.of(DEFINITION, count), registry.listTools());
        registry.dispose();
    }

    @Test
    public void nullResultIsAnExecutionFailureAndReleasesTheTool() {
        var disposed = new AtomicInteger();
        var registry = new AgentToolRegistry(List.of(source(DEFINITION, new AgentTool<Arguments, Result>() {
            public Result execute(Arguments arguments) { return null; }
            public void dispose() { disposed.incrementAndGet(); }
        })));

        assertEquals(EXECUTION_ERROR,
                registry.execute("find", "{\"label\":\"Pizza\",\"scope\":\"ALL\"}").status());
        registry.dispose();
        assertEquals(1, disposed.get());
    }

    public static class BrokenResult {
        public String getValue() { throw new IllegalStateException("Cannot read result"); }
    }

    @Test
    public void serializationFailureIsAnExecutionFailure() {
        var definition = new AgentToolDefinition<>("broken", "Broken result", Arguments.class, BrokenResult.class);
        var registry = new AgentToolRegistry(List.of(source(definition, arguments -> new BrokenResult())));

        assertEquals(EXECUTION_ERROR,
                registry.execute("broken", "{\"label\":\"Pizza\",\"scope\":\"ALL\"}").status());
        registry.dispose();
    }

    @Test
    public void definitionRequiresNameDescriptionAndBothTypes() {
        assertThrows(IllegalArgumentException.class,
                () -> new AgentToolDefinition<>(" ", "Find", Arguments.class, Result.class));
        assertThrows(IllegalArgumentException.class,
                () -> new AgentToolDefinition<>("find", " ", Arguments.class, Result.class));
        assertThrows(NullPointerException.class,
                () -> new AgentToolDefinition<>("find", "Find", null, Result.class));
        assertThrows(NullPointerException.class,
                () -> new AgentToolDefinition<>("find", "Find", Arguments.class, null));
    }
}
