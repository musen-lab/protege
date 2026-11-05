package org.protege.ai.core;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.Test;
import org.protege.ai.api.AgentTool;
import org.protege.ai.api.AgentToolDescription;
import org.protege.ai.api.AgentToolSource;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;
import static org.protege.ai.api.AgentToolResult.Status.*;

public class AgentToolRegistryTest {

    private static final AgentToolDescription DESCRIPTION =
            new AgentToolDescription("test_tool", "Test tool", "{\"type\":\"object\"}", "application/json");

    private AgentToolSource source(String id, AgentTool tool) {
        return source(id, DESCRIPTION, tool);
    }

    private AgentToolSource source(String id, AgentToolDescription description, AgentTool tool) {
        return new AgentToolSource() {
            public String getId() { return id; }
            public AgentToolDescription getDescription() { return description; }
            public AgentTool newInstance() { return tool; }
        };
    }

    @Test
    public void listsFindsAndInvokesSuppliedTool() {
        AgentToolRegistry registry = new AgentToolRegistry(List.of(source("first", input -> input)));
        assertEquals(List.of(DESCRIPTION), registry.listTools());
        assertEquals(DESCRIPTION, registry.findTool("test_tool").orElseThrow());
        assertEquals("{\"value\":7}", registry.execute("test_tool", "{\"value\":7}").content());
        assertEquals(SUCCESS, registry.execute("test_tool", "{}").status());
        assertThrows(UnsupportedOperationException.class, () -> registry.listTools().clear());
    }

    @Test
    public void unknownToolReturnsAnErrorWithoutThrowing() {
        AgentToolRegistry registry = new AgentToolRegistry(List.of());
        assertTrue(registry.findTool("missing").isEmpty());
        assertEquals(NO_SUCH_TOOL, registry.execute("missing", "{}").status());
        assertEquals("No such tool: missing", registry.execute("missing", "{}").content());
    }

    @Test
    public void rejectsDuplicateBeforeInstantiationAndLogsBothSources() {
        Logger logger = (Logger) LoggerFactory.getLogger(AgentToolRegistry.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            AgentToolRegistry registry = new AgentToolRegistry(List.of(source("plugin.first", input -> "first")));
            AgentToolSource duplicate = new AgentToolSource() {
                public String getId() { return "plugin.second"; }
                public AgentToolDescription getDescription() { return DESCRIPTION; }
                public AgentTool newInstance() { throw new AssertionError("Must not construct duplicate"); }
            };
            assertFalse(registry.register(duplicate));
            assertEquals("first", registry.execute("test_tool", "{}").content());
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
        AgentTool tool = new AgentTool() {
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
        assertEquals(REGISTRY_CLOSED, registry.execute("test_tool", "{}").status());
        assertThrows(IllegalStateException.class, () -> registry.register(source("second", tool)));
    }

    @Test
    public void cleansUpFailedInitializationAndLoadsNextSource() {
        AtomicInteger disposed = new AtomicInteger();
        AgentTool broken = new AgentTool() {
            public void initialise() { throw new IllegalStateException("Failed to initialize"); }
            public String execute(String input) { throw new AssertionError(); }
            public void dispose() { disposed.incrementAndGet(); }
        };
        AgentToolRegistry registry = new AgentToolRegistry(List.of(
                source("broken", broken), source("working",
                new AgentToolDescription("other_tool", "Other tool", "{}", "application/json"), input -> "{}")));
        assertEquals(1, disposed.get());
        assertEquals(NO_SUCH_TOOL, registry.execute("test_tool", "{}").status());
        assertEquals(SUCCESS, registry.execute("other_tool", "{}").status());
    }

    @Test
    public void failedSourceStillClaimsItsNameAndLogsBothSources() {
        Logger logger = (Logger) LoggerFactory.getLogger(AgentToolRegistry.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            AgentTool broken = new AgentTool() {
                public void initialise() { throw new IllegalStateException("Cannot initialize"); }
                public String execute(String input) { throw new AssertionError(); }
            };
            AgentToolRegistry registry = new AgentToolRegistry(List.of(source("plugin.broken", broken)));
            AgentToolSource duplicate = new AgentToolSource() {
                public String getId() { return "plugin.duplicate"; }
                public AgentToolDescription getDescription() { return DESCRIPTION; }
                public AgentTool newInstance() { throw new AssertionError("Must not construct duplicate"); }
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
        assertEquals(EXECUTION_ERROR, registry.execute("test_tool", "fail").status());
        assertEquals("Tool execution failed: test_tool: Failure details", registry.execute("test_tool", "fail").content());
        assertEquals(SUCCESS, registry.execute("test_tool", "{}").status());
    }

    @Test
    public void assertionErrorReturnsAnErrorAndReleasesTheCall() {
        AtomicInteger disposed = new AtomicInteger();
        AgentTool tool = new AgentTool() {
            public String execute(String input) { throw new AssertionError("Broken tool assumption"); }
            public void dispose() { disposed.incrementAndGet(); }
        };
        AgentToolRegistry registry = new AgentToolRegistry(List.of(source("first", tool)));
        var result = registry.execute("test_tool", "{}");
        assertEquals(EXECUTION_ERROR, result.status());
        assertEquals("Tool execution failed: test_tool: Broken tool assumption", result.content());
        registry.dispose();
        assertEquals(1, disposed.get());
    }

    @Test
    public void virtualMachineErrorPropagatesAndReleasesTheCall() {
        VirtualMachineError failure = new InternalError("Simulated VM failure");
        AtomicInteger disposed = new AtomicInteger();
        AgentTool tool = new AgentTool() {
            public String execute(String input) { throw failure; }
            public void dispose() { disposed.incrementAndGet(); }
        };
        AgentToolRegistry registry = new AgentToolRegistry(List.of(source("first", tool)));
        assertSame(failure, assertThrows(VirtualMachineError.class, () -> registry.execute("test_tool", "{}")));
        registry.dispose();
        assertEquals(1, disposed.get());
    }

    @Test
    public void failureWithoutMessageIncludesExceptionType() {
        AgentToolRegistry registry = new AgentToolRegistry(List.of(source("first", input -> {
            throw new IllegalStateException();
        })));
        assertEquals("Tool execution failed: test_tool: IllegalStateException",
                registry.execute("test_tool", "{}").content());
    }

    @Test
    public void interruptionPreservesTheThreadInterruptFlag() {
        AgentToolRegistry registry = new AgentToolRegistry(List.of(source("first", input -> {
            throw new InterruptedException();
        })));
        try {
            assertEquals(EXECUTION_ERROR, registry.execute("test_tool", "{}").status());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    public void registriesDoNotShareInstances() {
        AtomicInteger instances = new AtomicInteger();
        AgentToolSource source = new AgentToolSource() {
            public String getId() { return "factory"; }
            public AgentToolDescription getDescription() { return DESCRIPTION; }
            public AgentTool newInstance() {
                String instance = Integer.toString(instances.incrementAndGet());
                return input -> instance;
            }
        };
        AgentToolRegistry first = new AgentToolRegistry(List.of(source));
        AgentToolRegistry second = new AgentToolRegistry(List.of(source));
        assertNotEquals(first.execute("test_tool", "{}").content(), second.execute("test_tool", "{}").content());
        first.dispose();
        assertEquals(SUCCESS, second.execute("test_tool", "{}").status());
    }
}
