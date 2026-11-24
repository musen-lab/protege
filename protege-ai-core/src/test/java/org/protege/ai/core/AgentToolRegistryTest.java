package org.protege.ai.core;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.Test;
import org.protege.ai.api.AgentTool;
import org.protege.ai.api.AgentToolDefinition;
import org.protege.ai.api.AgentToolSource;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;
import static org.protege.ai.api.ToolStatus.*;

public class AgentToolRegistryTest {

    private static final AgentToolDefinition<String, String> DEFINITION =
            new AgentToolDefinition<String, String>("test_tool", "Test tool", String.class, String.class);

    private AgentToolSource<String, String> source(String id, ToolStub.Invocation<String, String> invocation) {
        return source(id, DEFINITION, new ToolStub<>(DEFINITION, invocation));
    }

    private AgentToolSource<String, String> source(String id, AgentToolDefinition<String, String> definition,
                                                  ToolStub.Invocation<String, String> invocation) {
        return source(id, definition, new ToolStub<>(definition, invocation));
    }

    private AgentToolSource<String, String> source(String id, AgentTool<String, String> tool) {
        return source(id, DEFINITION, tool);
    }

    private AgentToolSource<String, String> source(String id, AgentToolDefinition<String, String> description, AgentTool<String, String> tool) {
        return new AgentToolSource<String, String>() {
            public String getId() { return id; }
            public AgentToolDefinition<String, String> getDefinition() { return description; }
            public AgentTool<String, String> newInstance() { return tool; }
        };
    }

    @Test
    public void rejectsMismatchedOrMissingDefinitionsBeforeInitializationAndDisposesTool() {
        var differentName = new AgentToolDefinition<>("other", "Test tool", String.class, String.class);
        var differentDescription = new AgentToolDefinition<>("test_tool", "Other description", String.class, String.class);
        Logger logger = (Logger) LoggerFactory.getLogger(AgentToolRegistry.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            for (var definition : Arrays.asList(differentName, differentDescription, null)) {
                var initialized = new AtomicInteger();
                var disposed = new AtomicInteger();
                AgentTool<String, String> tool = new AgentTool<>() {
                    public AgentToolDefinition<String, String> getDefinition() { return definition; }
                    public void initialise() { initialized.incrementAndGet(); }
                    public String execute(String input) { throw new AssertionError("Must not execute rejected tool"); }
                    public void dispose() { disposed.incrementAndGet(); }
                };
                var registry = new AgentToolRegistry(List.of(source("plugin.mismatch", tool)));
                assertTrue(registry.listTools().isEmpty());
                assertTrue(registry.findSchemas(DEFINITION.name()).isEmpty());
                assertEquals(NO_SUCH_TOOL, registry.execute(DEFINITION.name(), "\"input\"").status());
                assertEquals(0, initialized.get());
                assertEquals(1, disposed.get());
                registry.dispose();
                assertEquals(1, disposed.get());
            }
            assertEquals(3, appender.list.size());
            for (var event : appender.list) {
                assertTrue(event.getFormattedMessage().contains("plugin.mismatch"));
                assertEquals("Tool definition does not match its factory's definition",
                        event.getThrowableProxy().getMessage());
            }
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    public void acceptsEqualDefinitionsWithoutRequiringTheSameObject() {
        var equivalent = new AgentToolDefinition<>(DEFINITION.name(), DEFINITION.description(),
                DEFINITION.argumentType(), DEFINITION.resultType());
        assertNotSame(DEFINITION, equivalent);
        var registry = new AgentToolRegistry(List.of(source("plugin.equivalent",
                new ToolStub<>(equivalent, input -> input))));
        assertEquals(SUCCESS, registry.execute(DEFINITION.name(), "\"input\"").status());
        registry.dispose();
    }

    @Test
    public void listsFindsAndInvokesSuppliedTool() {
        AgentToolRegistry registry = new AgentToolRegistry(List.of(source("first", input -> input)));
        assertEquals(List.of(DEFINITION), registry.listTools());
        assertEquals(DEFINITION, registry.findTool("test_tool").orElseThrow());
        assertEquals("\"hello\"", registry.execute("test_tool", "\"hello\"").content());
        assertEquals(SUCCESS, registry.execute("test_tool", "\"{}\"").status());
        assertThrows(UnsupportedOperationException.class, () -> registry.listTools().clear());
    }

    @Test
    public void unknownToolReturnsAnErrorWithoutThrowing() {
        AgentToolRegistry registry = new AgentToolRegistry(List.of());
        assertTrue(registry.findTool("missing").isEmpty());
        assertEquals(NO_SUCH_TOOL, registry.execute("missing", "\"{}\"").status());
        assertEquals("No such tool: missing", registry.execute("missing", "\"{}\"").content());
    }

    @Test
    public void rejectsDuplicateBeforeInstantiationAndLogsBothSources() {
        Logger logger = (Logger) LoggerFactory.getLogger(AgentToolRegistry.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            AgentToolRegistry registry = new AgentToolRegistry(List.of(source("plugin.first", input -> "first")));
            AgentToolSource<String, String> duplicate = new AgentToolSource<String, String>() {
                public String getId() { return "plugin.second"; }
                public AgentToolDefinition<String, String> getDefinition() { return DEFINITION; }
                public AgentTool<String, String> newInstance() { throw new AssertionError("Must not construct duplicate"); }
            };
            assertFalse(registry.register(duplicate));
            assertEquals("\"first\"", registry.execute("test_tool", "\"{}\"").content());
            assertTrue(appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                    .anyMatch(message -> message.contains("plugin.first") && message.contains("plugin.second")));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    public void initializesAndDisposesOwnedToolsExactlyOnce() {
        AtomicInteger initialized = new AtomicInteger();
        AtomicInteger disposed = new AtomicInteger();
        AgentTool<String, String> tool = new AgentTool<String, String>() {
            public AgentToolDefinition<String, String> getDefinition() { return DEFINITION; }
            public void initialise() { initialized.incrementAndGet(); }
            public String execute(String input) { return "{}"; }
            public void dispose() { disposed.incrementAndGet(); }
        };
        AgentToolRegistry registry = new AgentToolRegistry(List.of(source("first", tool)));
        assertEquals(1, initialized.get());
        registry.dispose();
        registry.dispose();
        assertEquals(1, disposed.get());
        assertTrue(registry.listTools().isEmpty());
        assertEquals(REGISTRY_CLOSED, registry.execute("test_tool", "\"{}\"").status());
        assertThrows(IllegalStateException.class, () -> registry.register(source("second", tool)));
    }

    @Test
    public void cleansUpFailedInitializationAndLoadsNextSource() {
        AtomicInteger disposed = new AtomicInteger();
        AgentTool<String, String> broken = new AgentTool<String, String>() {
            public AgentToolDefinition<String, String> getDefinition() { return DEFINITION; }
            public void initialise() { throw new IllegalStateException("Failed to initialize"); }
            public String execute(String input) { throw new AssertionError(); }
            public void dispose() { disposed.incrementAndGet(); }
        };
        AgentToolRegistry registry = new AgentToolRegistry(List.of(
                source("broken", broken), source("working",
                new AgentToolDefinition<String, String>("other_tool", "Other tool", String.class, String.class), input -> "{}")));
        assertEquals(1, disposed.get());
        assertEquals(NO_SUCH_TOOL, registry.execute("test_tool", "\"{}\"").status());
        assertEquals(SUCCESS, registry.execute("other_tool", "\"{}\"").status());
    }

    @Test
    public void failedSourceStillClaimsItsNameAndLogsBothSources() {
        Logger logger = (Logger) LoggerFactory.getLogger(AgentToolRegistry.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            AgentTool<String, String> broken = new AgentTool<String, String>() {
                public AgentToolDefinition<String, String> getDefinition() { return DEFINITION; }
                public void initialise() { throw new IllegalStateException("Cannot initialize"); }
                public String execute(String input) { throw new AssertionError(); }
            };
            AgentToolRegistry registry = new AgentToolRegistry(List.of(source("plugin.broken", broken)));
            AgentToolSource<String, String> duplicate = new AgentToolSource<String, String>() {
                public String getId() { return "plugin.duplicate"; }
                public AgentToolDefinition<String, String> getDefinition() { return DEFINITION; }
                public AgentTool<String, String> newInstance() { throw new AssertionError("Must not construct duplicate"); }
            };
            assertFalse(registry.register(duplicate));
            assertTrue(registry.listTools().isEmpty());
            assertTrue(appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                    .anyMatch(message -> message.contains("plugin.broken") && message.contains("plugin.duplicate")));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    public void nullInputIsRejectedBeforeCallingTool() {
        AgentToolRegistry registry = new AgentToolRegistry(List.of(source("first", input -> {
            throw new AssertionError("Must not execute with null input");
        })));
        assertEquals(INVALID_INPUT, registry.execute("test_tool", null).status());
        assertEquals("Tool input must not be null", registry.execute("test_tool", null).content());
    }

    @Test
    public void toolFailureIsReportedWithoutLosingTheTool() {
        AgentToolRegistry registry = new AgentToolRegistry(List.of(source("first", input -> {
            if (input.equals("fail")) {
                throw new IllegalArgumentException("Failure details");
            }
            return input;
        })));
        assertEquals(EXECUTION_ERROR, registry.execute("test_tool", "\"fail\"").status());
        assertFalse(registry.execute("test_tool", "\"fail\"").content().contains("Failure details"));
        assertEquals(SUCCESS, registry.execute("test_tool", "\"{}\"").status());
    }

    @Test
    public void assertionErrorReturnsAnErrorAndReleasesTheCall() {
        AtomicInteger disposed = new AtomicInteger();
        AgentTool<String, String> tool = new AgentTool<String, String>() {
            public AgentToolDefinition<String, String> getDefinition() { return DEFINITION; }
            public String execute(String input) { throw new AssertionError("Broken tool assumption"); }
            public void dispose() { disposed.incrementAndGet(); }
        };
        AgentToolRegistry registry = new AgentToolRegistry(List.of(source("first", tool)));
        var result = registry.execute("test_tool", "\"{}\"");
        assertEquals(EXECUTION_ERROR, result.status());
        assertFalse(result.content().contains("Broken tool assumption"));
        assertTrue(result.content().contains("reference"));
        registry.dispose();
        assertEquals(1, disposed.get());
    }

    @Test
    public void virtualMachineErrorPropagatesAndReleasesTheCall() {
        VirtualMachineError failure = new InternalError("Simulated VM failure");
        AtomicInteger disposed = new AtomicInteger();
        AgentTool<String, String> tool = new AgentTool<String, String>() {
            public AgentToolDefinition<String, String> getDefinition() { return DEFINITION; }
            public String execute(String input) { throw failure; }
            public void dispose() { disposed.incrementAndGet(); }
        };
        AgentToolRegistry registry = new AgentToolRegistry(List.of(source("first", tool)));
        assertSame(failure, assertThrows(VirtualMachineError.class, () -> registry.execute("test_tool", "\"{}\"")));
        registry.dispose();
        assertEquals(1, disposed.get());
    }

    @Test
    public void failureWithoutMessageStillHasDiagnosticReference() {
        AgentToolRegistry registry = new AgentToolRegistry(List.of(source("first", input -> {
            throw new IllegalStateException();
        })));
        assertTrue(registry.execute("test_tool", "\"{}\"").content().contains("reference"));
    }

    @Test
    public void interruptionPreservesTheThreadInterruptFlag() {
        AgentToolRegistry registry = new AgentToolRegistry(List.of(source("first", input -> {
            throw new InterruptedException();
        })));
        try {
            assertEquals(INTERRUPTED, registry.execute("test_tool", "\"{}\"").status());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    public void registriesDoNotShareInstances() {
        AtomicInteger instances = new AtomicInteger();
        AgentToolSource<String, String> source = new AgentToolSource<String, String>() {
            public String getId() { return "factory"; }
            public AgentToolDefinition<String, String> getDefinition() { return DEFINITION; }
            public AgentTool<String, String> newInstance() {
                String instance = Integer.toString(instances.incrementAndGet());
                return new ToolStub<>(DEFINITION, input -> instance);
            }
        };
        AgentToolRegistry first = new AgentToolRegistry(List.of(source));
        AgentToolRegistry second = new AgentToolRegistry(List.of(source));
        assertNotEquals(first.execute("test_tool", "\"{}\"").content(), second.execute("test_tool", "\"{}\"").content());
        first.dispose();
        assertEquals(SUCCESS, second.execute("test_tool", "\"{}\"").status());
    }
}
