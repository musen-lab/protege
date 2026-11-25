package org.protege.ai.core;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.Test;
import org.protege.ai.api.AgentTool;
import org.protege.ai.api.AgentToolDefinition;
import org.protege.ai.api.AgentToolFactory;
import org.protege.ai.core.plugin.AgentToolPluginImpl;
import org.protege.editor.core.Disposable;
import org.protege.editor.core.editorkit.plugin.EditorKitHookPluginImpl;
import org.protege.editor.owl.OWLEditorKit;
import org.protege.editor.owl.model.OWLModelManager;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.protege.ai.api.ToolStatus.*;

public class AgentToolPluginTest {

    private static final AgentToolDefinition<String, String> DEFINITION =
            new AgentToolDefinition<String, String>("test_tool", "Test tool", String.class, String.class);

    private OWLEditorKit editorKit() {
        var kit = mock(OWLEditorKit.class);
        var manager = mock(OWLModelManager.class);
        when(kit.getOWLModelManager()).thenReturn(manager);
        when(manager.getOWLDataFactory()).thenReturn(OWLManager.getOWLDataFactory());
        Map<Object, Disposable> objects = new HashMap<>();
        when(kit.get(any())).thenAnswer(call -> objects.get(call.getArgument(0)));
        doAnswer(call -> {
            objects.put(call.getArgument(0), call.getArgument(1));
            return null;
        }).when(kit).put(any(), any());
        return kit;
    }

    public static class ClassFactory implements AgentToolFactory<OWLClass, OWLClass> {
        public AgentToolDefinition<OWLClass, OWLClass> getDefinition() {
            return new AgentToolDefinition<>("class_reference", "Return a class", OWLClass.class, OWLClass.class);
        }
        public AgentTool<OWLClass, OWLClass> createTool(OWLEditorKit kit) {
            return new ToolStub<>(getDefinition(), input -> input);
        }
    }

    @Test
    public void jsonServiceUsesTheWindowsDataFactoryForClassArguments() throws Exception {
        var kit = editorKit();
        var factory = mock(OWLDataFactory.class);
        var iri = IRI.create("https://example.org/Pizza");
        var owlClass = OWLManager.getOWLDataFactory().getOWLClass(iri);
        when(factory.getOWLClass(iri)).thenReturn(owlClass);
        when(kit.getOWLModelManager().getOWLDataFactory()).thenReturn(factory);
        var extension = TestPluginRuntime.extension("test.owlClass", ClassFactory.class.getName());
        try (var runtime = new TestPluginRuntime(extension)) {
            var registry = AgentToolServices.getRegistry(kit);
            try {
                var result = AgentToolServices.getJsonTools(kit).execute("class_reference", "\"https://example.org/Pizza\"");
                assertEquals(SUCCESS, result.status());
                verify(factory).getOWLClass(iri);
            } finally {
                registry.dispose();
            }
        }
    }

    public static class TestFactory implements AgentToolFactory<String, String> {
        static final AtomicInteger factories = new AtomicInteger();
        static final AtomicInteger tools = new AtomicInteger();
        static final AtomicInteger disposed = new AtomicInteger();
        static OWLEditorKit receivedKit;

        public TestFactory() { factories.incrementAndGet(); }
        public AgentToolDefinition<String, String> getDefinition() { return DEFINITION; }
        public AgentTool<String, String> createTool(OWLEditorKit kit) {
            receivedKit = kit;
            tools.incrementAndGet();
            return new AgentTool<String, String>() {
                public AgentToolDefinition<String, String> getDefinition() { return DEFINITION; }
                public String execute(String input) { return input; }
                public void dispose() { disposed.incrementAndGet(); }
            };
        }
    }

    public static class BlankNameFactory implements AgentToolFactory<String, String> {
        public AgentToolDefinition<String, String> getDefinition() {
            return new AgentToolDefinition<String, String>(" ", "Invalid", String.class, String.class);
        }
        public AgentTool<String, String> createTool(OWLEditorKit kit) {
            throw new AssertionError("Must not create a tool with invalid metadata");
        }
    }

    @Test
    public void definitionComesFromFactoryBeforeToolConstruction() throws Exception {
        TestFactory.factories.set(0);
        TestFactory.tools.set(0);
        var extension = TestPluginRuntime.extension("test.mapping", TestFactory.class.getName());
        var kit = editorKit();
        try (var runtime = new TestPluginRuntime(extension)) {
            var plugin = new AgentToolPluginImpl<String, String>(extension, kit);
            assertEquals("test.mapping", plugin.getId());
            assertEquals(DEFINITION, plugin.getDefinition());
            assertEquals(1, TestFactory.factories.get());
            assertEquals(0, TestFactory.tools.get());
            assertEquals(DEFINITION, plugin.getDefinition());
            AgentTool<String, String> tool = plugin.newInstance();
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
        // Stands in for the model manager's object map, to which the real OWLEditorKit forwards put/get.
        Map<Object, Disposable> objects = new HashMap<>();
        OWLEditorKit kit = editorKit();
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
            var jsonTools = AgentToolServices.getJsonTools(kit);
            AgentToolRegistry registry = AgentToolServices.getRegistry(kit);
            assertEquals(1, objects.size());
            assertTrue(objects.containsValue(jsonTools));
            assertSame(jsonTools, AgentToolServices.getJsonTools(kit));
            assertEquals(1, TestFactory.tools.get());
            var hookExtension = TestPluginRuntime.extension("test.hook", AgentToolRegistryInitializer.class.getName());
            var hook = new EditorKitHookPluginImpl(hookExtension, kit).newInstance();
            hook.initialise();
            assertSame(registry, AgentToolServices.getRegistry(kit));
            assertEquals(1, TestFactory.tools.get());
            hook.dispose();
            assertEquals(0, TestFactory.disposed.get());
            kit.dispose();
            assertEquals(1, TestFactory.disposed.get());
            assertEquals(REGISTRY_CLOSED, registry.execute("test_tool", "input").status());
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
            var registry = AgentToolServices.getRegistry(editorKit());
            assertEquals(java.util.List.of(DEFINITION), registry.listTools());
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
