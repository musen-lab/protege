package org.protege.ai.core;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.OptBoolean;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.protege.ai.api.AgentTool;
import org.protege.ai.api.AgentToolDefinition;
import org.protege.ai.api.AgentToolResult;
import org.protege.ai.api.AgentToolSource;
import org.protege.ai.api.ToolNullable;
import org.protege.ai.api.ToolFailure;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;
import static org.protege.ai.api.ToolStatus.*;

public class AgentToolValidationTest {

    public enum Scope { DIRECT, ALL }

    public record Search(String label,
                         @JsonProperty(isRequired = OptBoolean.FALSE, defaultValue = "\"ALL\"") Scope scope,
                         @JsonProperty(isRequired = OptBoolean.FALSE) @ToolNullable String note) {}

    public record RequiredNullable(@ToolNullable String note) {}
    public record NullableDefault(@JsonProperty(isRequired = OptBoolean.FALSE, defaultValue = "\"ALL\"")
                                  @ToolNullable Scope scope) {}
    public record ArrayInput(@ToolNullable String[] items) {}

    private <I, O> AgentToolRegistry registry(Class<I> input, Class<O> output, ToolStub.Invocation<I, O> invocation) {
        return new AgentToolRegistry(List.of(new AgentToolSource<I, O>() {
            public String getId() { return "test.validation"; }
            public AgentToolDefinition<I, O> getDefinition() {
                return new AgentToolDefinition<>("test", "Validate arguments and results", input, output);
            }
            public AgentTool<I, O> newInstance() { return new ToolStub<>(getDefinition(), invocation); }
        }));
    }

    @Test
    public void omissionUsesDefaultsWhileExplicitNullRequiresPermission() {
        var received = new AtomicReference<Search>();
        var calls = new AtomicInteger();
        var registry = registry(Search.class, String.class, args -> {
            calls.incrementAndGet();
            received.set(args);
            return "ok";
        });
        assertEquals(SUCCESS, registry.execute("test", "{\"label\":\"Pizza\"}").status());
        assertEquals(new Search("Pizza", Scope.ALL, null), received.get());
        assertEquals(SUCCESS, registry.execute("test", "{\"label\":\"Pizza\",\"scope\":\"DIRECT\",\"note\":null}").status());
        assertEquals(new Search("Pizza", Scope.DIRECT, null), received.get());
        for (String input : List.of("{}", "null", " ", "{\"label\":null}",
                "{\"label\":\"Pizza\",\"scope\":null}",
                "{\"label\":\"Pizza\",\"scope\":\"WRONG\"}",
                "{\"label\":\"Pizza\",\"extra\":true}",
                "{\"label\":\"Pizza\",\"label\":\"Other\"}")) {
            assertEquals(input, INVALID_INPUT, registry.execute("test", input).status());
        }
        assertEquals(2, calls.get());
        assertTrue(registry.execute("test", "{\"label\":\"Pizza\",\"scope\":null}").content().contains("scope"));
        registry.dispose();
    }

    @Test
    public void nullableDoesNotMakeAFieldOptional() {
        var registry = registry(RequiredNullable.class, RequiredNullable.class, args -> args);
        assertEquals(INVALID_INPUT, registry.execute("test", "{}").status());
        assertEquals(SUCCESS, registry.execute("test", "{\"note\":null}").status());
        assertEquals("{\"note\":null}", registry.execute("test", "{\"note\":null}").content());
        registry.dispose();
    }

    @Test
    public void nullableDefaultsPreserveExplicitNullAndArraysSupportNullableElements() {
        var registry = registry(NullableDefault.class, NullableDefault.class, args -> args);
        assertEquals("{\"scope\":\"ALL\"}", registry.execute("test", "{}").content());
        assertEquals("{\"scope\":null}", registry.execute("test", "{\"scope\":null}").content());
        registry.dispose();
        var arrays = registry(ArrayInput.class, ArrayInput.class, args -> args);
        assertEquals(SUCCESS, arrays.execute("test", "{\"items\":[null,\"text\"]}").status());
        assertEquals(INVALID_INPUT, arrays.execute("test", "{\"items\":null}").status());
        arrays.dispose();
    }

    public record CollectionsInput(List<@ToolNullable String> items,
                                   @ToolNullable List<String> list,
                                   List<List<@ToolNullable String>> nested) {}

    @Test
    public void distinguishesNullableListsFromNullableElementsInBothDirections() {
        var registry = registry(CollectionsInput.class, CollectionsInput.class, args -> args);
        String valid = "{\"items\":[null,\"x\"],\"list\":null,\"nested\":[[null],[]]}";
        var result = registry.execute("test", valid);
        assertEquals(result.content() + " " + registry.findSchemas("test").orElseThrow().inputSchema(), SUCCESS, result.status());
        for (String input : List.of(
                "{\"items\":null,\"list\":null,\"nested\":[]}",
                "{\"items\":[],\"list\":[null],\"nested\":[]}",
                "{\"items\":[],\"list\":[],\"nested\":[null]}")) {
            assertEquals(input, INVALID_INPUT, registry.execute("test", input).status());
        }
        registry.dispose();
    }

    public record Child(@JsonProperty(isRequired = OptBoolean.FALSE, defaultValue = "3") int count) {}
    public record Parent(@JsonProperty(isRequired = OptBoolean.FALSE, defaultValue = "{}") Child child,
                         @JsonProperty(isRequired = OptBoolean.FALSE, defaultValue = "[{}]") List<Child> children) {}

    @Test
    public void appliesNestedObjectAndListDefaultsWithoutSharingThemBetweenCalls() {
        var received = new AtomicReference<Parent>();
        var registry = registry(Parent.class, String.class, args -> {
            received.set(args);
            return "ok";
        });
        assertEquals(SUCCESS, registry.execute("test", "{}").status());
        Parent first = received.get();
        assertEquals(new Parent(new Child(3), List.of(new Child(3))), first);
        first.children().clear();
        assertEquals(SUCCESS, registry.execute("test", "{}").status());
        assertEquals(new Parent(new Child(3), List.of(new Child(3))), received.get());
        assertNotSame(first.children(), received.get().children());
        registry.dispose();
    }

    public static class Bean {
        @JsonProperty(isRequired = OptBoolean.FALSE)
        public @ToolNullable String note = "initializer";
        public int count;
    }

    @Test
    public void validatesRequiredBeanFieldsAndAssignsNullToOmittedOptionalFields() {
        var received = new AtomicReference<Bean>();
        var registry = registry(Bean.class, String.class, args -> { received.set(args); return "ok"; });
        assertEquals(INVALID_INPUT, registry.execute("test", "{}").status());
        assertEquals(SUCCESS, registry.execute("test", "{\"count\":7.0}").status());
        assertNull(received.get().note);
        assertEquals(7, received.get().count);
        for (String value : List.of("7.1", "2147483648", "7.0000000000000001")) {
            assertEquals(INVALID_INPUT, registry.execute("test", "{\"count\":" + value + "}").status());
        }
        registry.dispose();
    }

    public record Strings(List<String> values) {}
    public record DefaultResult(@JsonProperty(isRequired = OptBoolean.FALSE, defaultValue = "\"ALL\"") Scope scope) {}

    @Test
    public void checksReturnedValuesAndNeverRepairsResultsWithDefaults() {
        var badItems = registry(String.class, Strings.class, args -> new Strings(Arrays.asList("x", null)));
        var result = badItems.execute("test", "\"x\"");
        assertTrue(result instanceof ToolFailure);
        assertEquals(EXECUTION_ERROR, result.status());
        badItems.dispose();
        var missing = registry(String.class, DefaultResult.class, args -> new DefaultResult(null));
        assertEquals(EXECUTION_ERROR, missing.execute("test", "\"x\"").status());
        missing.dispose();
    }

    public record RequiredDefault(@JsonProperty(isRequired = OptBoolean.TRUE, defaultValue = "\"ALL\"") Scope scope) {}
    public record OptionalWithoutValue(@JsonProperty(isRequired = OptBoolean.FALSE) String text) {}
    public record BadJson(@JsonProperty(isRequired = OptBoolean.FALSE, defaultValue = "ALL") Scope scope) {}
    public record BadEnum(@JsonProperty(isRequired = OptBoolean.FALSE, defaultValue = "\"WRONG\"") Scope scope) {}
    public record BadNull(@JsonProperty(isRequired = OptBoolean.FALSE, defaultValue = "null") String text) {}
    public record BadNumber(@JsonProperty(isRequired = OptBoolean.FALSE, defaultValue = "2147483648") int count) {}
    public record NullablePrimitive(@ToolNullable int count) {}
    public record RecursiveDefault(@JsonProperty(isRequired = OptBoolean.FALSE, defaultValue = "{}") RecursiveDefault next) {}

    @Test
    public void rejectsInvalidDeclarationsBeforeConstructingATool() {
        for (Class<?> input : List.of(RequiredDefault.class, OptionalWithoutValue.class, BadJson.class,
                BadEnum.class, BadNull.class, BadNumber.class, NullablePrimitive.class, RecursiveDefault.class)) {
            var created = new AtomicInteger();
            var registry = new AgentToolRegistry(List.of(new AgentToolSource<Object, String>() {
                public String getId() { return "test.invalid"; }
                @SuppressWarnings("unchecked")
                public AgentToolDefinition<Object, String> getDefinition() {
                    return new AgentToolDefinition<>("test", "Invalid declaration", (Class<Object>) input, String.class);
                }
                public AgentTool<Object, String> newInstance() { created.incrementAndGet(); return new ToolStub<>(getDefinition(), args -> "ok"); }
            }));
            assertEquals(input.getName(), 0, created.get());
            assertTrue(input.getName(), registry.listTools().isEmpty());
            registry.dispose();
        }
    }

    @Test
    public void discoveryAdvertisesTheSameOptionalDefaultAndNullRules() throws Exception {
        var registry = registry(Search.class, String.class, args -> "ok");
        var schema = new ObjectMapper().readTree(registry.findSchemas("test").orElseThrow().inputSchema());
        assertEquals("[\"label\"]", schema.get("required").toString());
        assertEquals("ALL", schema.at("/properties/scope/default").asText());
        assertTrue(schema.at("/properties/note/type").toString().contains("null"));
        registry.dispose();
    }

    @Test
    public void loadsSchemaResourcesEvenWhenTheCallerCannotSeeThem() {
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(new ClassLoader(null) {});
            var registry = registry(Search.class, String.class, args -> "ok");
            assertEquals(SUCCESS, registry.execute("test", "{\"label\":\"Pizza\"}").status());
            registry.dispose();
        } finally {
            thread.setContextClassLoader(previous);
        }
    }
}
