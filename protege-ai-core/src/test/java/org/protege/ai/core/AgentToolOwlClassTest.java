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
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.protege.ai.api.ToolStatus.*;

public class AgentToolOwlClassTest {

    private static final String PIZZA = "https://example.org/Pizza";
    private final ObjectMapper mapper = new ObjectMapper();

    public record Arguments(OWLClass owlClass) {}
    public record Result(OWLClass owlClass, List<OWLClass> classes) {}
    public record Nested(List<List<@ToolNullable OWLClass>> groups, OWLClass[] classes) {}
    public record Defaults(
            @JsonProperty(isRequired = OptBoolean.FALSE, defaultValue = "\"https://example.org/Pizza\"") OWLClass owlClass,
            @JsonProperty(isRequired = OptBoolean.FALSE, defaultValue = "\"https://example.org/Pizza\"") @ToolNullable OWLClass nullable,
            @JsonProperty(isRequired = OptBoolean.FALSE) @ToolNullable OWLClass omitted) {}
    public record BadDefault(@JsonProperty(isRequired = OptBoolean.FALSE, defaultValue = "\"Pizza\"") OWLClass owlClass) {}
    public record BadListDefault(@JsonProperty(isRequired = OptBoolean.FALSE, defaultValue = "[\"https://example.org/%ZZ\"]") List<OWLClass> classes) {}
    public record BadSchemeDefault(@JsonProperty(isRequired = OptBoolean.FALSE, defaultValue = "\"1:\"") OWLClass owlClass) {}

    private <I, O> AgentToolSource<I, O> source(Class<I> input, Class<O> output, ToolStub.Invocation<I, O> invocation) {
        var definition = new AgentToolDefinition<>("test", "Test class conversion", input, output);
        return new AgentToolSource<>() {
            public String getId() { return "test.owlClass"; }
            public AgentToolDefinition<I, O> getDefinition() { return definition; }
            public AgentTool<I, O> newInstance() { return new ToolStub<>(definition, invocation); }
        };
    }

    @Test
    public void convertsFieldsUsingSuppliedFactoryAndRetainsOriginalResult() throws Exception {
        var factory = mock(OWLDataFactory.class);
        var iri = IRI.create(PIZZA);
        var owlClass = OWLManager.getOWLDataFactory().getOWLClass(iri);
        when(factory.getOWLClass(iri)).thenReturn(owlClass);
        var returned = new AtomicReference<Result>();
        var registry = new AgentToolRegistry(List.of(source(Arguments.class, Result.class, args -> {
            assertSame(owlClass, args.owlClass());
            var value = new Result(args.owlClass(), List.of(args.owlClass()));
            returned.set(value);
            return value;
        })), factory);
        try {
            var result = registry.execute("test", "{\"owlClass\":\"" + PIZZA + "\"}");
            assertTrue(result.content(), result instanceof AgentToolResult.Success<?>);
            var success = (AgentToolResult.Success<?>) result;
            assertSame(returned.get(), success.value());
            assertEquals(mapper.readTree("{\"owlClass\":\"" + PIZZA + "\",\"classes\":[\"" + PIZZA + "\"]}"),
                    mapper.readTree(success.json()));
            verify(factory).getOWLClass(iri);
            var schemas = registry.findSchemas("test").orElseThrow();
            for (String schema : List.of(schemas.inputSchema(), schemas.outputSchema())) {
                var tree = mapper.readTree(schema);
                var property = tree.at("/properties/owlClass");
                assertEquals("string", property.path("type").asText());
                assertEquals("iri", property.path("format").asText());
                assertEquals("^[A-Za-z][A-Za-z0-9+.-]*:", property.path("pattern").asText());
                assertFalse(property.has("properties"));
            }
        } finally {
            registry.dispose();
        }
    }

    @Test
    public void rootClassesPreserveUnicodeEscapingAndSchemesWithoutPrefixExpansion() throws Exception {
        var registry = new AgentToolRegistry(List.of(source(OWLClass.class, OWLClass.class, value -> value)));
        try {
            for (String iri : List.of(PIZZA, "https://例え.jp/ピザ#種類", "urn:example:Pizza",
                    "https://example.org/A%20B", "pizza:Pizza", "http://www.w3.org/2002/07/owl#Thing")) {
                var result = registry.execute("test", mapper.writeValueAsString(iri));
                assertTrue(result.content(), result instanceof AgentToolResult.Success<?>);
                var success = (AgentToolResult.Success<?>) result;
                assertEquals(iri, ((OWLClass) success.value()).getIRI().toString());
                assertEquals(iri, mapper.readTree(success.json()).asText());
            }
        } finally {
            registry.dispose();
        }
    }

    @Test
    public void rejectsMalformedRelativeAndNonStringInputsBeforeExecution() throws Exception {
        var calls = new AtomicInteger();
        var registry = new AgentToolRegistry(List.of(source(Arguments.class, Arguments.class, value -> {
            calls.incrementAndGet();
            return value;
        })));
        try {
            for (String iri : List.of("", "Pizza", "#Pizza", "../Pizza", "//example.org/Pizza", "//", "1:", "://",
                    "https://example.org/a b", "https://example.org/%ZZ", "http://[invalid]/Pizza")) {
                var result = registry.execute("test", "{\"owlClass\":" + mapper.writeValueAsString(iri) + "}");
                assertEquals(iri, INVALID_INPUT, result.status());
                assertTrue(result.content().contains("owlClass"));
            }
            for (String value : List.of("42", "true", "null", "{}", "[]")) {
                assertEquals(INVALID_INPUT, registry.execute("test", "{\"owlClass\":" + value + "}").status());
            }
            assertEquals(INVALID_INPUT, registry.execute("test", "{}").status());
            assertEquals(0, calls.get());
        } finally {
            registry.dispose();
        }
    }

    @Test
    public void handlesArraysAndNestedNullableLists() throws Exception {
        var registry = new AgentToolRegistry(List.of(source(Nested.class, Nested.class, value -> value)));
        try {
            String input = "{\"groups\":[[\"" + PIZZA + "\",null]],\"classes\":[\"" + PIZZA + "\"]}";
            var result = registry.execute("test", input);
            assertTrue(result.content(), result instanceof AgentToolResult.Success<?>);
            var value = (Nested) ((AgentToolResult.Success<?>) result).value();
            assertEquals(IRI.create(PIZZA), value.groups().getFirst().getFirst().getIRI());
            assertNull(value.groups().getFirst().get(1));
            assertEquals(value.groups().getFirst().getFirst(), value.classes()[0]);
            assertEquals(mapper.readTree(input), mapper.readTree(result.content()));
            assertEquals(INVALID_INPUT, registry.execute("test", "{\"groups\":[[\"Pizza\"]],\"classes\":[]}").status());
            assertEquals(INVALID_INPUT, registry.execute("test", "{\"groups\":[null],\"classes\":[]}").status());
            assertEquals(INVALID_INPUT, registry.execute("test", "{\"groups\":[],\"classes\":[null]}").status());
        } finally {
            registry.dispose();
        }
    }

    @Test
    public void appliesClassDefaultsAndPreservesExplicitNullableValues() {
        var registry = new AgentToolRegistry(List.of(source(Defaults.class, Defaults.class, value -> value)));
        try {
            var result = registry.execute("test", "{}");
            assertTrue(result.content(), result instanceof AgentToolResult.Success<?>);
            var value = (Defaults) ((AgentToolResult.Success<?>) result).value();
            assertEquals(IRI.create(PIZZA), value.owlClass().getIRI());
            assertEquals(value.owlClass(), value.nullable());
            assertNull(value.omitted());
            result = registry.execute("test", "{\"nullable\":null}");
            assertTrue(result.content(), result instanceof AgentToolResult.Success<?>);
            assertNull(((Defaults) ((AgentToolResult.Success<?>) result).value()).nullable());
            assertEquals(INVALID_INPUT, registry.execute("test", "{\"owlClass\":null}").status());
        } finally {
            registry.dispose();
        }
    }

    @Test
    public void rejectsInvalidClassDefaultsBeforeToolConstruction() {
        assertRejectedDefault(BadDefault.class);
        assertRejectedDefault(BadListDefault.class);
        assertRejectedDefault(BadSchemeDefault.class);
    }

    private <I> void assertRejectedDefault(Class<I> input) {
        var constructed = new AtomicInteger();
        var registry = new AgentToolRegistry(List.of());
        try {
            assertFalse(registry.register(new AgentToolSource<I, String>() {
                public String getId() { return "test.badClassDefault"; }
                public AgentToolDefinition<I, String> getDefinition() {
                    return new AgentToolDefinition<>("test", "Bad default", input, String.class);
                }
                public AgentTool<I, String> newInstance() {
                    constructed.incrementAndGet();
                    return new ToolStub<>(getDefinition(), value -> "unused");
                }
            }));
            assertEquals(0, constructed.get());
            assertTrue(registry.findSchemas("test").isEmpty());
        } finally {
            registry.dispose();
        }
    }

    @Test
    public void invalidClassResultsReturnFailureWithoutExposingTheObject() {
        for (String iri : List.of("Pizza", "https://example.org/a b", "//", "1:")) {
            var invalid = OWLManager.getOWLDataFactory().getOWLClass(IRI.create(iri));
            var registry = new AgentToolRegistry(List.of(source(String.class, Result.class,
                    value -> new Result(invalid, List.of(invalid)))));
            try {
                var result = registry.execute("test", "\"input\"");
                assertTrue(result instanceof ToolFailure);
                assertEquals(EXECUTION_ERROR, result.status());
            } finally {
                registry.dispose();
            }
        }
    }
}
