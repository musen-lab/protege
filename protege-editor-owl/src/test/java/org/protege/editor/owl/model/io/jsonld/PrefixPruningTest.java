package org.protege.editor.owl.model.io.jsonld;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Prefixes are kept only when the finished document uses them in an IRI, not when text happens to start with them.
 */
public class PrefixPruningTest {

    private static final Set<String> PREFIXES = new HashSet<>(Arrays.asList("ex", "rdfs", "xsd", "other"));

    private static final String PREFIX_CONTEXT = "\"ex\": \"http://example.org/\", \"rdfs\": \"http://www.w3.org/2000/01/rdf-schema#\", " +
            "\"xsd\": \"http://www.w3.org/2001/XMLSchema#\", \"other\": \"http://other.org/\"";

    private final ObjectMapper mapper = new ObjectMapper();

    /** Prunes a document with the given extra context entries and @graph, and returns the prefixes left. */
    private Set<String> prefixesLeft(String extraContext, String graph) throws Exception {
        ObjectNode root = (ObjectNode) mapper.readTree("{\"@context\": {" + PREFIX_CONTEXT
                + (extraContext.isEmpty() ? "" : ", " + extraContext) + "}, \"@graph\": " + graph + "}");
        JsonLdPostProcessor.removeUnusedPrefixes(root, PREFIXES);
        Set<String> left = new HashSet<>();
        root.get("@context").fieldNames().forEachRemaining(name -> {
            if (PREFIXES.contains(name)) {
                left.add(name);
            }
        });
        return left;
    }

    @Test
    public void plainTextIsNotAnIri() throws Exception {
        Set<String> left = prefixesLeft("\"note\": \"http://example.org/note\"",
                "[{\"@id\": \"ex:a\", \"note\": \"rdfs:foo\"}]");
        assertEquals(new HashSet<>(Arrays.asList("ex")), left);
    }

    @Test
    public void valuesAreNotIris() throws Exception {
        Set<String> left = prefixesLeft("", "[{\"@id\": \"ex:a\", \"ex:note\": {\"@value\": \"rdfs:foo\", \"@language\": \"en\"}}]");
        assertEquals(new HashSet<>(Arrays.asList("ex")), left);
    }

    @Test
    public void languageMapsHoldText() throws Exception {
        Set<String> left = prefixesLeft("\"label\": {\"@id\": \"http://www.w3.org/2000/01/rdf-schema#label\", \"@container\": \"@language\"}",
                "[{\"@id\": \"ex:a\", \"label\": {\"en\": \"rdfs:foo\", \"fr\": [\"other:bar\"]}}]");
        assertEquals(new HashSet<>(Arrays.asList("ex")), left);
    }

    @Test
    public void plainStringsOfIriValuedPropertiesAreIris() throws Exception {
        Set<String> left = prefixesLeft("\"eats\": {\"@id\": \"http://example.org/eats\", \"@type\": \"@id\"}",
                "[{\"@id\": \"ex:a\", \"eats\": \"other:tom\"}]");
        assertTrue(left.contains("other"));
    }

    @Test
    public void listsOfIriValuedPropertiesAreIris() throws Exception {
        Set<String> left = prefixesLeft("\"members\": {\"@id\": \"http://example.org/members\", \"@type\": \"@id\"}",
                "[{\"@id\": \"ex:a\", \"members\": {\"@list\": [\"other:x\", \"other:y\"]}}]");
        assertTrue(left.contains("other"));
    }

    @Test
    public void idsTypesAndDatatypesAreIris() throws Exception {
        Set<String> left = prefixesLeft("",
                "[{\"@id\": \"other:a\", \"@type\": [\"rdfs:Class\"], \"ex:age\": {\"@value\": \"5\", \"@type\": \"xsd:integer\"}}]");
        assertEquals(PREFIXES, left);
    }

    @Test
    public void nestedNodesAndTheirReferencesCount() throws Exception {
        Set<String> left = prefixesLeft("",
                "[{\"@id\": \"ex:a\", \"ex:p\": {\"@type\": \"rdfs:Class\", \"ex:q\": {\"@id\": \"other:b\"}}}]");
        assertEquals(new HashSet<>(Arrays.asList("ex", "rdfs", "other")), left);
    }

    @Test
    public void prefixedKeysInTheBodyAndContextCount() throws Exception {
        assertTrue(prefixesLeft("", "[{\"@id\": \"ex:a\", \"rdfs:comment\": \"text\"}]").contains("rdfs"));
        assertTrue(prefixesLeft("\"rdfs:label\": {\"@id\": \"http://www.w3.org/2000/01/rdf-schema#label\", \"@container\": \"@language\"}",
                "[{\"@id\": \"ex:a\", \"rdfs:label\": {\"en\": \"A\"}}]").contains("rdfs"));
    }

    @Test
    public void prefixesUsedByContextEntriesCount() throws Exception {
        assertTrue(prefixesLeft("\"age\": {\"@id\": \"other:age\", \"@type\": \"xsd:integer\"}", "[{\"@id\": \"ex:a\"}]")
                .containsAll(Arrays.asList("other", "xsd")));
    }
}
