package org.protege.ai.core;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.Test;
import org.protege.ai.api.AgentTool;
import org.protege.ai.api.AgentToolDescription;
import org.protege.ai.api.AgentToolFactory;
import org.protege.ai.core.plugin.AgentToolPluginImpl;
import org.protege.editor.core.Disposable;
import org.protege.editor.core.editorkit.plugin.EditorKitHookPluginImpl;
import org.protege.editor.owl.OWLEditorKit;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.protege.ai.api.AgentToolResult.Status.*;

public class AgentToolPluginTest {

    private static final AgentToolDescription DESCRIPTION =
            new AgentToolDescription("test_tool", "Test tool", "{\"type\":\"object\"}", "The input text unchanged.");

    public static class TestFactory implements AgentToolFactory {
        static final AtomicInteger factories = new AtomicInteger();
        static final AtomicInteger tools = new AtomicInteger();
        static final AtomicInteger disposed = new AtomicInteger();
        static OWLEditorKit receivedKit;

        public TestFactory() { factories.incrementAndGet(); }
        public AgentToolDescription getDescription() { return DESCRIPTION; }
        public AgentTool createTool(OWLEditorKit kit) {
            receivedKit = kit;
            tools.incrementAndGet();
            return new AgentTool() {
                public String execute(String input) { return input; }
                public void dispose() { disposed.incrementAndGet(); }
            };
        }
    }

    public static class BlankNameFactory implements AgentToolFactory {
        public AgentToolDescription getDescription() {
            return new AgentToolDescription(" ", "Invalid", "{}", "Text");
        }
        public AgentTool createTool(OWLEditorKit kit) {
            throw new AssertionError("Must not create a tool with invalid metadata");
        }
    }

    @Test
    public void descriptionComesFromFactoryBeforeToolConstruction() throws Exception {
        TestFactory.factories.set(0);
        TestFactory.tools.set(0);
        var extension = TestPluginRuntime.extension("test.mapping", TestFactory.class.getName());
        var kit = mock(OWLEditorKit.class);
        try (var runtime = new TestPluginRuntime(extension)) {
            var plugin = new AgentToolPluginImpl(extension, kit);
            assertEquals("test.mapping", plugin.getId());
            assertEquals(DESCRIPTION, plugin.getDescription());
            assertEquals(1, TestFactory.factories.get());
            assertEquals(0, TestFactory.tools.get());
            assertEquals(DESCRIPTION, plugin.getDescription());
            AgentTool tool = plugin.newInstance();
            assertEquals(1, TestFactory.factories.get());
            assertEquals(1, TestFactory.tools.get());
            assertSame(kit, TestFactory.receivedKit);
            assertEquals("hello", tool.execute("hello"));
            tool.dispose();
        }
    }

    @Test
    public void editorKitAndHookShareOneRegistryAndDisposeItsTools() throws Exception {
        TestFactory.tools.set(0);
        TestFactory.disposed.set(0);
        var extension = TestPluginRuntime.extension("test.lifecycle", TestFactory.class.getName());
        Map<Object, Disposable> objects = new HashMap<>();
        OWLEditorKit kit = mock(OWLEditorKit.class);
        when(kit.get(any())).thenAnswer(call -> objects.get(call.getArgument(0)));
        doAnswer(call -> {
            objects.put(call.getArgument(0), call.getArgument(1));
            return null;
        }).when(kit).put(any(), any());
        doAnswer(call -> {
            for (Disposable object : objects.values()) {
                object.dispose();
            }
            objects.clear();
            return null;
        }).when(kit).dispose();

        try (var runtime = new TestPluginRuntime(extension)) {
            AgentToolRegistry registry = AgentToolRegistry.getRegistry(kit);
            assertEquals(1, objects.size());
            assertTrue(objects.containsValue(registry));
            assertEquals(1, TestFactory.tools.get());
            var hookExtension = TestPluginRuntime.extension("test.hook", AgentToolRegistryInitializer.class.getName());
            var hook = new EditorKitHookPluginImpl(hookExtension, kit).newInstance();
            hook.initialise();
            assertSame(registry, AgentToolRegistry.getRegistry(kit));
            assertEquals(1, TestFactory.tools.get());
            hook.dispose();
            assertEquals(0, TestFactory.disposed.get());
            kit.dispose();
            assertEquals(1, TestFactory.disposed.get());
            assertEquals(REGISTRY_CLOSED, registry.execute("test_tool", "{}").status());
            registry.dispose();
            assertEquals(1, TestFactory.disposed.get());
        }
    }

    @Test
    public void malformedDeclarationsAreSkippedAndLogTheirSources() throws Exception {
        var blankName = TestPluginRuntime.extension("test.blankName", BlankNameFactory.class.getName());
        var blankClass = TestPluginRuntime.extension("test.blankClass", " ");
        var valid = TestPluginRuntime.extension("test.valid", TestFactory.class.getName());
        Logger logger = (Logger) LoggerFactory.getLogger(AgentToolRegistry.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try (var runtime = new TestPluginRuntime(blankName, blankClass, valid)) {
            var registry = AgentToolRegistry.getRegistry(mock(OWLEditorKit.class));
            assertEquals(java.util.List.of(DESCRIPTION), registry.listTools());
            assertTrue(appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                    .anyMatch(message -> message.contains("test.blankName")));
            assertTrue(appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                    .anyMatch(message -> message.contains("test.blankClass")));
            registry.dispose();
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
