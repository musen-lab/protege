package org.protege.editor.owl.model.io.jsonld;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.jsonldjava.core.JsonLdConsts;
import com.github.jsonldjava.core.JsonLdOptions;
import com.github.jsonldjava.core.JsonLdProcessor;
import com.github.jsonldjava.utils.JsonUtils;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

public class JsonLdPostProcessorTest {

    private static final String XSD = "http://www.w3.org/2001/XMLSchema#";

    private static final String DRAFT = "["
            + "{\"@id\":\"urn:subject\","
            + "\"@type\":[\"urn:Class\"],"
            + "\"urn:link\":[{\"@id\":\"_:shared\"},{\"@id\":\"_:shared\"}],"
            + "\"urn:list\":[{\"@list\":[{\"@id\":\"_:shared\"},{\"@value\":\"bonjour\",\"@language\":\"fr\"}]}],"
            + "\"urn:integer\":[{\"@value\":\"01\",\"@type\":\"" + XSD + "integer\"}],"
            + "\"urn:boolean\":[{\"@value\":\"true\",\"@type\":\"" + XSD + "boolean\"}]},"
            + "{\"@id\":\"_:shared\",\"urn:text\":[{\"@value\":\"é\\n\\\"quoted\\\"\"}]},"
            + "{\"@id\":\"urn:graph\",\"@graph\":[{\"@id\":\"urn:other\","
            + "\"urn:link\":[{\"@id\":\"urn:subject\"}]}]}]";

    @Test
    public void directDatasetPreservesTheRdfOfThePreviousTextPipeline() throws Exception {
        for (boolean nativeTypes : new boolean[]{false, true}) {
            for (boolean rdfType : new boolean[]{false, true}) {
                JsonLdPreferences prefs = new JsonLdPreferences(InMemoryPreferences::new);
                prefs.setUseNativeTypes(nativeTypes);
                prefs.setUseRdfType(rdfType);

                JsonLdOptions toRdf = new JsonLdOptions();
                toRdf.format = JsonLdConsts.APPLICATION_NQUADS;
                Object nquads = JsonLdProcessor.toRDF(JsonUtils.fromString(DRAFT), toRdf);
                JsonLdOptions fromRdf = new JsonLdOptions();
                fromRdf.format = JsonLdConsts.APPLICATION_NQUADS;
                fromRdf.setUseNativeTypes(nativeTypes);
                fromRdf.setUseRdfType(rdfType);
                Object previous = JsonLdProcessor.fromRDF(nquads, fromRdf);

                Object current = rebuild(prefs);
                // Canonical RDF compares meaning regardless of node order and blank-node labels.
                assertEquals(canonicalRdf(previous), canonicalRdf(current));
                JsonNode subject = subject(current);
                assertEquals("Duplicate RDF values must stay deduplicated", 1, subject.path("urn:link").size());
                assertEquals(2, subject.path("urn:list").get(0).path("@list").size());
                // Preserve the library's treatment of non-canonical integer lexical forms such as "01".
                assertEquals(subject(previous).path("urn:integer"), subject.path("urn:integer"));
                assertEquals(nativeTypes, subject.path("urn:boolean").get(0).path("@value").isBoolean());
                assertEquals(!rdfType, subject.has("@type"));
                assertEquals(rdfType, subject.has("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"));
            }
        }
    }

    private static Object rebuild(JsonLdPreferences prefs) throws Exception {
        try (ByteArrayInputStream input = new ByteArrayInputStream(DRAFT.getBytes(StandardCharsets.UTF_8))) {
            return JsonLdPostProcessor.rebuild(input, prefs);
        }
    }

    private static Object canonicalRdf(Object expanded) throws Exception {
        JsonLdOptions options = new JsonLdOptions();
        options.format = JsonLdConsts.APPLICATION_NQUADS;
        return JsonLdProcessor.normalize(expanded, options);
    }

    private static JsonNode subject(Object expanded) {
        for (JsonNode node : new ObjectMapper().valueToTree(expanded)) {
            if ("urn:subject".equals(node.path("@id").asText())) {
                return node;
            }
        }
        throw new AssertionError("Missing subject");
    }
}
