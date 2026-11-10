package org.protege.editor.owl.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

/** Checks record binding and compatibility with the bundled YAML parser. */
public class JacksonCompatibilityTest {

    public enum Scope { DIRECT, ALL }

    public record Arguments(@JsonProperty("classIri") String iri, Scope scope) {}

    public record Result(List<Arguments> matches) {}

    @Test
    public void readsRecordsWithRenamedFieldsEnumsAndNestedLists() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Result result = mapper.readValue("""
                {"matches":[{"classIri":"http://example.org/Pizza","scope":"DIRECT"}]}
                """, Result.class);

        assertEquals(new Result(List.of(new Arguments("http://example.org/Pizza", Scope.DIRECT))), result);
    }

    @Test
    public void writesRecordsUsingTheirDeclaredPropertyNames() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Result result = new Result(List.of(new Arguments("http://example.org/Pizza", Scope.ALL)));

        assertEquals(mapper.readTree("""
                {"matches":[{"classIri":"http://example.org/Pizza","scope":"ALL"}]}
                """), mapper.readTree(mapper.writeValueAsString(result)));
        assertEquals(mapper.readTree("{\"matches\":[]}"),
                mapper.readTree(mapper.writeValueAsString(new Result(List.of()))));
    }

    @Test
    public void readsYamlWithTheBundledSnakeYamlVersion() throws Exception {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

        assertEquals("Pizza", mapper.readTree("label: Pizza\n").get("label").asText());
    }
}
