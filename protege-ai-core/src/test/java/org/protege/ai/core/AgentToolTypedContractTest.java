package org.protege.ai.core;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.protege.ai.api.AgentTool;
import org.protege.ai.api.AgentToolDefinition;
import org.protege.ai.api.AgentToolResult;
import org.protege.ai.api.AgentToolSource;
import org.protege.ai.api.ToolFailure;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;
import static org.protege.ai.api.ToolStatus.*;

public class AgentToolTypedContractTest {

    public enum Scope { DIRECT, ALL }

    public record Arguments(@JsonProperty("label") String name, Scope scope) {}

    public record Match(String label) {}

    public record Result(List<Match> matches) {}

    private static final AgentToolDefinition<Arguments, Result> DEFINITION =
            new AgentToolDefinition<>("find", "Find matching labels", Arguments.class, Result.class);

    private <I, O> AgentToolSource<I, O> source(AgentToolDefinition<I, O> definition,
                                              ToolStub.Invocation<I, O> invocation) {
        return source(definition, new ToolStub<>(definition, invocation));
    }

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
        var returned = new AtomicReference<Result>();
        var calls = new AtomicInteger();
        var jsonTools = new AgentToolJsonService(List.of(source(DEFINITION, arguments -> {
            calls.incrementAndGet();
            received.set(arguments);
            var result = new Result(List.of(new Match(arguments.name())));
            returned.set(result);
            return result;
        })));

        var result = jsonTools.execute("find", "{\"label\":\"Pizza\",\"scope\":\"DIRECT\"}");

        assertEquals(SUCCESS, result.status());
        assertTrue(result instanceof AgentToolResult.Success<?>);
        var success = (AgentToolResult.Success<?>) result;
        assertSame(returned.get(), success.value());
        assertSame(result.content(), success.json());
        assertEquals(1, calls.get());
        assertEquals(new Arguments("Pizza", Scope.DIRECT), received.get());
        var mapper = new ObjectMapper();
        assertEquals(mapper.readTree("{\"matches\":[{\"label\":\"Pizza\"}]}"), mapper.readTree(result.content()));
        assertSame(Arguments.class, jsonTools.registry().findTool("find").orElseThrow().argumentType());
        assertSame(Result.class, jsonTools.registry().findTool("find").orElseThrow().resultType());
        jsonTools.dispose();
        assertEquals(List.of(new Match("Pizza")), ((Result) success.value()).matches());
    }

    public static class ReadOnceResult {
        private int reads;

        public String getValue() {
            if (++reads > 1) {
                throw new IllegalStateException("Result was serialized more than once");
            }
            return "value";
        }
    }

    @Test
    public void retainsOriginalBeanAndSerializesItOnlyOnce() {
        var definition = new AgentToolDefinition<>("bean", "Return a bean", String.class, ReadOnceResult.class);
        var original = new ReadOnceResult();
        var jsonTools = new AgentToolJsonService(List.of(source(definition, input -> original)));
        try {
            var result = jsonTools.execute("bean", "\"input\"");
            assertTrue(result.content(), result instanceof AgentToolResult.Success<?>);
            var success = (AgentToolResult.Success<?>) result;
            assertSame(original, success.value());
            assertEquals("{\"value\":\"value\"}", success.json());
            assertEquals(1, original.reads);
        } finally {
            jsonTools.dispose();
        }
    }

    @Test
    public void responsesRequireCompleteSuccessOrNonSuccessFailure() {
        assertThrows(NullPointerException.class, () -> new AgentToolResult.Success<>(null, "{}"));
        assertThrows(NullPointerException.class, () -> new AgentToolResult.Success<>(new Result(List.of()), null));
        assertThrows(NullPointerException.class, () -> new ToolFailure(null, "Failure"));
        assertThrows(NullPointerException.class, () -> new ToolFailure(EXECUTION_ERROR, null));
        assertThrows(IllegalArgumentException.class, () -> new ToolFailure(SUCCESS, "{}"));
        for (var status : List.of(NO_SUCH_TOOL, INVALID_INPUT, EXECUTION_ERROR, REGISTRY_CLOSED)) {
            var failure = new ToolFailure(status, "Failure");
            assertFalse(failure.isSuccess());
            assertEquals(status, failure.status());
            assertEquals("Failure", failure.content());
        }
    }

    @Test
    public void rejectsMalformedOrMismatchedArgumentsBeforeToolExecution() {
        var calls = new AtomicInteger();
        var jsonTools = new AgentToolJsonService(List.of(source(DEFINITION, arguments -> {
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
            assertEquals(input, INVALID_INPUT, jsonTools.execute("find", input).status());
        }
        assertEquals(0, calls.get());
        assertEquals("{\"matches\":[]}",
                jsonTools.execute("find", "{\"label\":\"Pizza\",\"scope\":\"ALL\"}").content());
        assertEquals(1, calls.get());
        jsonTools.dispose();
    }

    public record CountArguments(int count) {}

    public record CountResult(int count) {}

    @Test
    public void keepsDifferentToolContractsSeparate() {
        var count = new AgentToolDefinition<>("count", "Return a count", CountArguments.class, CountResult.class);
        var jsonTools = new AgentToolJsonService(List.of(
                source(DEFINITION, arguments -> new Result(List.of())),
                source(count, arguments -> new CountResult(arguments.count()))));

        assertEquals("{\"count\":7}", jsonTools.execute("count", "{\"count\":7}").content());
        assertEquals(INVALID_INPUT, jsonTools.execute("count", "{\"count\":\"7\"}").status());
        assertEquals(INVALID_INPUT, jsonTools.execute("count", "{\"count\":7.5}").status());
        assertEquals(INVALID_INPUT, jsonTools.execute("count", "{\"count\":null}").status());
        assertEquals(INVALID_INPUT, jsonTools.execute("find", "{\"count\":7}").status());
        assertEquals(List.of(DEFINITION, count), jsonTools.registry().listTools());
        jsonTools.dispose();
    }

    @Test
    public void nullResultIsAnExecutionFailureAndReleasesTheTool() {
        var disposed = new AtomicInteger();
        var jsonTools = new AgentToolJsonService(List.of(source(DEFINITION, new AgentTool<Arguments, Result>() {
            public AgentToolDefinition<Arguments, Result> getDefinition() { return DEFINITION; }
            public Result execute(Arguments arguments) { return null; }
            public void dispose() { disposed.incrementAndGet(); }
        })));

        var result = jsonTools.execute("find", "{\"label\":\"Pizza\",\"scope\":\"ALL\"}");
        assertTrue(result instanceof ToolFailure);
        assertEquals(EXECUTION_ERROR, result.status());
        jsonTools.dispose();
        assertEquals(1, disposed.get());
    }

    public static class BrokenResult {
        public String getValue() { throw new IllegalStateException("Cannot read result"); }
    }

    @Test
    public void serializationFailureIsAnExecutionFailure() {
        var definition = new AgentToolDefinition<>("broken", "Broken result", Arguments.class, BrokenResult.class);
        var jsonTools = new AgentToolJsonService(List.of(source(definition, arguments -> new BrokenResult())));

        var result = jsonTools.execute("broken", "{\"label\":\"Pizza\",\"scope\":\"ALL\"}");
        assertTrue(result instanceof ToolFailure);
        assertEquals(EXECUTION_ERROR, result.status());
        jsonTools.dispose();
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
