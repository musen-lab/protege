package org.protege.ai.core;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.Test;
import org.protege.ai.api.AgentTool;
import org.protege.ai.api.AgentToolDefinition;
import org.protege.ai.api.AgentToolException;
import org.protege.ai.api.ToolFailure.Detail;
import org.protege.ai.api.AgentToolSource;
import org.protege.ai.api.ToolFailure;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;
import static org.protege.ai.api.ToolStatus.*;

public class AgentToolErrorTest {

    public enum Scope { DIRECT, ALL }
    public record Query(Scope scope) {}
    public record Arguments(List<Query> queries) {}
    public record CheckedInput(String value) {
        public CheckedInput {
            if (value.equals("bad")) {
                throw new IllegalArgumentException("private constructor details");
            }
        }
    }

    private <I> AgentToolRegistry registry(Class<I> input, ToolStub.Invocation<I, String> action) {
        var definition = new AgentToolDefinition<>("test", "Test errors", input, String.class);
        return new AgentToolRegistry(List.of(new AgentToolSource<I, String>() {
            public String getId() { return "test.errors"; }
            public AgentToolDefinition<I, String> getDefinition() { return definition; }
            public AgentTool<I, String> newInstance() { return new ToolStub<>(definition, action); }
        }));
    }

    @Test
    public void validationReturnsFieldDetailsWithoutCallingTheTool() {
        var calls = new AtomicInteger();
        var registry = registry(Arguments.class, input -> {
            calls.incrementAndGet();
            return "ok";
        });
        try {
            var failure = (ToolFailure) registry.execute("test", "{\"queries\":[{\"scope\":\"INVALID\"}]}");
            assertEquals(INVALID_INPUT, failure.status());
            assertEquals("INVALID_INPUT", failure.code());
            assertEquals("/queries/0/scope", failure.details().getFirst().field());
            assertTrue(failure.details().getFirst().message().contains("DIRECT"));
            assertTrue(failure.content().contains(failure.details().getFirst().message()));
            var missing = (ToolFailure) registry.execute("test", "{\"queries\":[{}]}");
            assertEquals(INVALID_INPUT, missing.status());
            assertTrue(missing.details().getFirst().message().contains("scope"));
            assertEquals(0, calls.get());
            assertTrue(registry.execute("test", "{\"queries\":[{\"scope\":\"ALL\"}]}").isSuccess());
        } finally {
            registry.dispose();
        }
    }

    @Test
    public void malformedJsonAndBindingErrorsDoNotExposeConstructorMessages() {
        var calls = new AtomicInteger();
        var registry = registry(CheckedInput.class, input -> {
            calls.incrementAndGet();
            return "ok";
        });
        try {
            for (String json : List.of("{", "{\"value\":\"bad\"}")) {
                var failure = (ToolFailure) registry.execute("test", json);
                assertEquals(INVALID_INPUT, failure.status());
                assertEquals("INVALID_INPUT", failure.code());
                assertFalse(failure.content().contains("private constructor details"));
            }
            assertEquals(0, calls.get());
        } finally {
            registry.dispose();
        }
    }

    @Test
    public void expectedFailurePreservesTheToolsCodeMessageAndDetails() {
        var details = List.of(new Detail("/scope", "Inferred queries require an initialized reasoner."));
        var registry = registry(String.class, input -> {
            if (input.equals("inferred")) {
                throw new AgentToolException("REASONER_UNAVAILABLE", "Initialize the reasoner first.", details);
            }
            return input;
        });
        try {
            var failure = (ToolFailure) registry.execute("test", "\"inferred\"");
            assertEquals(TOOL_ERROR, failure.status());
            assertEquals("REASONER_UNAVAILABLE", failure.code());
            assertEquals("Initialize the reasoner first.", failure.content());
            assertEquals(details, failure.details());
            assertTrue(registry.execute("test", "\"asserted\"").isSuccess());
        } finally {
            registry.dispose();
        }
    }

    @Test
    public void unexpectedFailureReturnsAReferenceMatchingTheLoggedException() {
        var logger = (Logger) LoggerFactory.getLogger(AgentToolRegistry.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        var registry = registry(String.class, input -> {
            if (input.equals("fail")) {
                throw new IllegalStateException("private diagnostic details");
            }
            return input;
        });
        try {
            var failure = (ToolFailure) registry.execute("test", "\"fail\"");
            assertEquals(EXECUTION_ERROR, failure.status());
            assertEquals("EXECUTION_ERROR", failure.code());
            assertTrue(failure.details().isEmpty());
            assertFalse(failure.content().contains("private diagnostic details"));
            var event = appender.list.stream().filter(e -> e.getThrowableProxy() != null).findFirst().orElseThrow();
            assertEquals("private diagnostic details", event.getThrowableProxy().getMessage());
            String reference = event.getArgumentArray()[2].toString();
            assertNotNull(java.util.UUID.fromString(reference));
            assertTrue(failure.content().contains(reference));
            assertTrue(registry.execute("test", "\"ok\"").isSuccess());
        } finally {
            registry.dispose();
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    public void errorDetailsAreImmutableAndLegacyFailureConstructorStillWorks() {
        var details = new ArrayList<>(List.of(new Detail("/scope", "Choose DIRECT or ALL.")));
        var exception = new AgentToolException("BAD_SCOPE", "Unsupported scope.", details);
        var failure = new ToolFailure(TOOL_ERROR, exception.getMessage(), exception.code(), details);
        details.clear();
        assertEquals(1, exception.details().size());
        assertEquals(1, failure.details().size());
        assertThrows(UnsupportedOperationException.class, () -> failure.details().clear());
        assertThrows(UnsupportedOperationException.class, () -> exception.details().clear());
        assertEquals("NO_SUCH_TOOL", new ToolFailure(NO_SUCH_TOOL, "Missing tool.").code());
        assertThrows(IllegalArgumentException.class, () -> new AgentToolException(" ", "Message"));
        assertThrows(IllegalArgumentException.class, () -> new AgentToolException("CODE", " "));
        assertThrows(IllegalArgumentException.class, () -> new ToolFailure(TOOL_ERROR, "Message", "", List.of()));
    }
}
