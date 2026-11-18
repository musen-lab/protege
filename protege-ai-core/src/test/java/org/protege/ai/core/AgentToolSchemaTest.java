package org.protege.ai.core;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SpecVersion;
import org.junit.Test;
import org.protege.ai.api.AgentTool;
import org.protege.ai.api.AgentToolDefinition;
import org.protege.ai.api.AgentToolSource;
import org.protege.ai.api.ToolRegistry;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class AgentToolSchemaTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final JsonSchemaFactory validator = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);

    public enum Scope { @JsonProperty("direct") DIRECT, ALL }

    public record Match(String label) {}

    @JsonClassDescription("Search arguments")
    public record Arguments(@JsonProperty("label") @JsonPropertyDescription("Label to find") String name,
                            Scope scope, List<Match> matches) {}

    public record Result(List<Match> matches, Scope scope) {}

    public record Empty() {}

    private <I, O> AgentToolSource<I, O> source(Class<I> input, Class<O> output, ToolStub.Invocation<I, O> invocation) {
        return new AgentToolSource<>() {
            public String getId() { return "test.schema"; }
            public AgentToolDefinition<I, O> getDefinition() {
                return new AgentToolDefinition<>("test", "Test schemas", input, output);
            }
            public AgentTool<I, O> newInstance() { return new ToolStub<>(getDefinition(), invocation); }
        };
    }

    private JsonSchema checkedSchema(String json) throws Exception {
        JsonNode schema = mapper.readTree(json);
        // Check the generated document against the dialect's own schema, not just sample data.
        JsonSchema metaSchema = validator.getSchema(SchemaLocation.of(schema.get("$schema").asText()));
        assertTrue(metaSchema.validate(schema).toString(), metaSchema.validate(schema).isEmpty());
        return validator.getSchema(schema);
    }

    @Test
    public void schemasDescribeTheSameNamesEnumsAndNestedValuesAsInvocation() throws Exception {
        var registry = new AgentToolRegistry(List.of(source(Arguments.class, Result.class,
                args -> new Result(args.matches(), args.scope()))));
        ToolRegistry api = registry;
        var schemas = api.findSchemas("test").orElseThrow();
        var input = checkedSchema(schemas.inputSchema());
        var output = checkedSchema(schemas.outputSchema());
        var tree = mapper.readTree(schemas.inputSchema());
        assertEquals("Search arguments", tree.get("description").asText());
        assertEquals("Label to find", tree.at("/properties/label/description").asText());
        assertFalse(tree.get("properties").has("name"));
        for (String scope : List.of("direct", "ALL")) {
            String json = "{\"label\":\"Pizza\",\"scope\":\"" + scope + "\",\"matches\":[{\"label\":\"Pizza\"}]}";
            assertTrue(input.validate(mapper.readTree(json)).toString(), input.validate(mapper.readTree(json)).isEmpty());
            var result = api.execute("test", json);
            assertTrue(result.content(), result.isSuccess());
            assertTrue(output.validate(mapper.readTree(result.content())).isEmpty());
        }
        assertSame(schemas, api.findSchemas("test").orElseThrow());
        assertTrue(api.findSchemas("missing").isEmpty());
        registry.dispose();
        assertTrue(api.findSchemas("test").isEmpty());
    }

    @Test
    public void schemasRequireFieldsAndExcludeNullsUnknownFieldsAndWrongValues() throws Exception {
        var registry = new AgentToolRegistry(List.of(source(Arguments.class, Result.class,
                args -> new Result(args.matches(), args.scope()))));
        var schemas = registry.findSchemas("test").orElseThrow();
        var input = checkedSchema(schemas.inputSchema());
        for (String json : List.of("null", "{}",
                "{\"label\":null,\"scope\":\"ALL\",\"matches\":[]}",
                "{\"label\":42,\"scope\":\"ALL\",\"matches\":[]}",
                "{\"label\":\"Pizza\",\"scope\":\"DIRECT\",\"matches\":[]}",
                "{\"label\":\"Pizza\",\"scope\":\"ALL\",\"matches\":[null]}",
                "{\"label\":\"Pizza\",\"scope\":\"ALL\",\"matches\":[{}]}",
                "{\"label\":\"Pizza\",\"scope\":\"ALL\",\"matches\":[],\"extra\":true}")) {
            assertFalse(json, input.validate(mapper.readTree(json)).isEmpty());
        }
        var output = checkedSchema(schemas.outputSchema());
        assertTrue(output.validate(mapper.readTree("{\"matches\":[],\"scope\":\"ALL\"}")).isEmpty());
        assertFalse(output.validate(mapper.readTree("{\"matches\":[null],\"scope\":\"ALL\"}")).isEmpty());
        registry.dispose();
    }

    public static class CountBean {
        private int count;
        public int getCount() { return count; }
        public void setCount(int count) { this.count = count; }
    }

    public static class ComputedResult {
        public int getCount() { return 7; }
    }

    public record Node(String label, List<Node> children) {}

    @Test
    public void supportsBeanInputsComputedResultsAndRecursiveRecords() throws Exception {
        var beans = new AgentToolRegistry(List.of(source(CountBean.class, ComputedResult.class,
                args -> new ComputedResult())));
        var schemas = beans.findSchemas("test").orElseThrow();
        String json = "{\"count\":3}";
        assertTrue(checkedSchema(schemas.inputSchema()).validate(mapper.readTree(json)).isEmpty());
        var result = beans.execute("test", json);
        assertTrue(result.isSuccess());
        assertTrue(checkedSchema(schemas.outputSchema()).validate(mapper.readTree(result.content())).isEmpty());
        assertEquals("integer", mapper.readTree(schemas.outputSchema()).at("/properties/count/type").asText());
        beans.dispose();

        var recursive = new AgentToolRegistry(List.of(source(Node.class, Node.class, args -> args)));
        schemas = recursive.findSchemas("test").orElseThrow();
        json = "{\"label\":\"root\",\"children\":[{\"label\":\"leaf\",\"children\":[]}]}";
        assertTrue(checkedSchema(schemas.inputSchema()).validate(mapper.readTree(json)).isEmpty());
        result = recursive.execute("test", json);
        assertTrue(result.isSuccess());
        assertTrue(checkedSchema(schemas.outputSchema()).validate(mapper.readTree(result.content())).isEmpty());
        recursive.dispose();
    }

    @Test
    public void supportsEmptyRecordsAndBeanProperties() throws Exception {
        var registry = new AgentToolRegistry(List.of(source(Empty.class, CountBean.class, args -> new CountBean())));
        var schemas = registry.findSchemas("test").orElseThrow();
        var input = checkedSchema(schemas.inputSchema());
        assertTrue(input.validate(mapper.readTree("{}")).isEmpty());
        assertFalse(input.validate(mapper.readTree("{\"extra\":true}")).isEmpty());
        var result = registry.execute("test", "{}");
        assertTrue(result.isSuccess());
        var failures = checkedSchema(schemas.outputSchema()).validate(mapper.readTree(result.content()));
        assertTrue(failures.toString(), failures.isEmpty());
        assertEquals("integer", mapper.readTree(schemas.outputSchema()).at("/properties/count/type").asText());
        registry.dispose();
    }

    public record UnsupportedNested(List<OWLClassExpression> classes) {}
    public record RawCollection(List values) {}
    public record DynamicValue(Object value) {}
    public record UnappliedDefault(@JsonProperty(defaultValue = "ALL") Scope scope) {}
    public record CustomValue(String value) {
        @JsonValue public String json() { return value; }
    }
    public enum EmptyEnum {}
    public enum AmbiguousEnum { @JsonProperty("same") FIRST, @JsonProperty("same") SECOND }

    @Test
    public void unsupportedContractsAreRejectedBeforeConstructionAndNameTheSource() {
        var constructed = new AtomicInteger();
        Logger logger = (Logger) LoggerFactory.getLogger(AgentToolRegistry.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            for (Class<?> type : List.of(UnsupportedNested.class, RawCollection.class, DynamicValue.class,
                    Map.class, byte[].class, char[].class, UnappliedDefault.class, CustomValue.class,
                    EmptyEnum.class, AmbiguousEnum.class, int.class, TextNode.class)) {
                assertRejected(type, String.class, constructed);
                assertRejected(String.class, type, constructed);
            }
            assertEquals(0, constructed.get());
            assertEquals(24, appender.list.size());
            for (var event : appender.list) {
                assertTrue(event.getFormattedMessage().contains("test.unsupported"));
                assertTrue(event.getThrowableProxy().getMessage().contains("Unsupported tool contract"));
            }
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private <I, O> void assertRejected(Class<I> input, Class<O> output, AtomicInteger constructed) {
        var registry = new AgentToolRegistry(List.of());
        assertFalse(input + " / " + output, registry.register(new AgentToolSource<I, O>() {
            public String getId() { return "test.unsupported"; }
            public AgentToolDefinition<I, O> getDefinition() {
                return new AgentToolDefinition<>("test", "Unsupported", input, output);
            }
            public AgentTool<I, O> newInstance() {
                constructed.incrementAndGet();
                return new ToolStub<>(getDefinition(), args -> null);
            }
        }));
        assertTrue(registry.listTools().isEmpty());
        assertTrue(registry.findSchemas("test").isEmpty());
        registry.dispose();
    }
}
