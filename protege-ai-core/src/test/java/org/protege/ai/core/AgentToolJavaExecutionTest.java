package org.protege.ai.core;

import org.junit.Test;
import org.protege.ai.api.AgentTool;
import org.protege.ai.api.AgentToolDefinition;
import org.protege.ai.api.AgentToolException;
import org.protege.ai.api.AgentToolSource;
import org.protege.ai.api.ToolExecutionResult;
import org.protege.ai.api.ToolFailure;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;
import static org.protege.ai.api.ToolStatus.*;

public class AgentToolJavaExecutionTest {
    private <I, O> AgentToolSource<I, O> source(Class<I> input, Class<O> output, ToolStub.Invocation<I, O> action) {
        var definition = new AgentToolDefinition<>("test", "Java execution", input, output);
        return new AgentToolSource<>() {
            public String getId() { return "test.java"; }
            public AgentToolDefinition<I, O> getDefinition() { return definition; }
            public AgentTool<I, O> newInstance() { return new ToolStub<>(definition, action); }
        };
    }

    @Test
    public void javaExecutionDoesNotRequireAJsonSupportedContract() {
        var input = new StringBuilder("Java object");
        var source = source(StringBuilder.class, StringBuilder.class, value -> value);
        var registry = new AgentToolRegistry(List.of(source));
        var jsonTools = new AgentToolJsonService(List.of(source));
        try {
            var result = registry.execute("test", input);
            assertTrue(result.isSuccess());
            assertSame(input, ((ToolExecutionResult.Success<?>) result).value());
            assertTrue(jsonTools.registry().listTools().isEmpty());
            assertTrue(jsonTools.findSchemas("test").isEmpty());
        } finally {
            registry.dispose();
            jsonTools.dispose();
        }
    }

    @Test
    public void wrongJavaTypeNeverReachesTheToolAndStringsAreNotParsedAsJson() {
        var calls = new AtomicInteger();
        var registry = new AgentToolRegistry(List.of(source(String.class, String.class, value -> {
            calls.incrementAndGet();
            return value;
        })));
        try {
            assertEquals(INVALID_INPUT, registry.execute("test", 42).status());
            assertEquals(INVALID_INPUT, registry.execute("test", null).status());
            assertEquals(NO_SUCH_TOOL, registry.execute("missing", "hello").status());
            assertEquals(0, calls.get());
            var success = (ToolExecutionResult.Success<?>) registry.execute("test", "hello");
            assertEquals("hello", success.value());
            assertEquals(1, calls.get());
        } finally {
            registry.dispose();
        }
        assertEquals(REGISTRY_CLOSED, registry.execute("test", "hello").status());
    }

    @Test
    public void javaExecutionUsesSharedFailuresAndRecoversAfterEachFailure() {
        var registry = new AgentToolRegistry(List.of(source(String.class, String.class, value -> {
            return switch (value) {
                case "expected" -> throw new AgentToolException("UNAVAILABLE", "Try again later.");
                case "unexpected" -> throw new IllegalStateException("Private diagnostic");
                case "null" -> null;
                default -> value;
            };
        })));
        try {
            var expected = (ToolFailure) registry.execute("test", "expected");
            assertEquals(TOOL_ERROR, expected.status());
            assertEquals("UNAVAILABLE", expected.code());
            assertEquals("Try again later.", expected.content());
            var unexpected = (ToolFailure) registry.execute("test", "unexpected");
            assertEquals(EXECUTION_ERROR, unexpected.status());
            assertFalse(unexpected.content().contains("Private diagnostic"));
            assertTrue(unexpected.content().contains("reference"));
            assertEquals(EXECUTION_ERROR, registry.execute("test", "null").status());
            assertTrue(registry.execute("test", "ok").isSuccess());
        } finally {
            registry.dispose();
        }
    }
}
