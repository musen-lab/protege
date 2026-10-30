package org.protege.editor.owl.model.io.jsonld;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.semanticweb.owlapi.model.IRI;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.Assert.*;

public class JsonLdPrefixReaderTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    /** The entities of the ontology the context belongs to, as the loaded ontology would report them. */
    private static final Set<String> ENTITIES = new HashSet<>(Arrays.asList(
            "http://example.org/zoo#age",
            "http://example.org/zoo#eats",
            "http://example.org/zoo#Cat",
            "http://www.w3.org/2000/01/rdf-schema#label"));

    private static final Predicate<String> IS_ENTITY = ENTITIES::contains;

    private static Map<String, String> read(String json) throws Exception {
        return JsonLdPrefixReader.readPrefixes(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), IS_ENTITY);
    }

    @Test
    public void readsPrefixesButNotShortNames() throws Exception {
        Map<String, String> prefixes = read("{\"@context\": {" +
                "\"ex\": \"http://example.org/zoo#\"," +
                "\"owl\": \"http://www.w3.org/2002/07/owl#\"," +
                "\"age\": \"http://example.org/zoo#age\"," +
                "\"Cat\": \"http://example.org/zoo#Cat\"," +
                "\"label\": {\"@id\": \"http://www.w3.org/2000/01/rdf-schema#label\", \"@container\": \"@language\"}," +
                "\"eats\": {\"@id\": \"http://example.org/zoo#eats\", \"@type\": \"@id\"}," +
                "\"other:ex\": {\"@id\": \"http://other.org/ns#ex\"}" +
                "}, \"@graph\": []}");
        assertEquals("http://example.org/zoo#", prefixes.get("ex:"));
        assertEquals("http://www.w3.org/2002/07/owl#", prefixes.get("owl:"));
        assertEquals(2, prefixes.size());
    }

    @Test
    public void unusedNamespacesOfAnyShapeArePrefixes() throws Exception {
        // Nothing in the document uses these, and they don't all end in # or /
        Map<String, String> prefixes = read("{\"@context\": {" +
                "\"GO\": \"http://purl.obolibrary.org/obo/GO_\"," +
                "\"ex\": \"urn:example:\"," +
                "\"q\": \"http://example.org/find?\"" +
                "}, \"@graph\": []}");
        assertEquals("http://purl.obolibrary.org/obo/GO_", prefixes.get("GO:"));
        assertEquals("urn:example:", prefixes.get("ex:"));
        assertEquals("http://example.org/find?", prefixes.get("q:"));
    }

    @Test
    public void textInTheDocumentDoesNotMatter() throws Exception {
        Map<String, String> prefixes = read("{\"@context\": {\"age\": \"http://example.org/zoo#age\"}, " +
                "\"@graph\": [{\"@id\": \"http://example.org/zoo#tom\", \"note\": \"tag:abc and age:3\"}]}");
        assertTrue(prefixes.isEmpty());
    }

    @Test
    public void explicitPrefixDefinitionsArePrefixes() throws Exception {
        Map<String, String> prefixes = read("{\"@context\": {" +
                "\"obo\": {\"@id\": \"http://purl.obolibrary.org/obo/\", \"@prefix\": true}," +
                "\"eats\": {\"@id\": \"http://example.org/zoo#eats\", \"@type\": \"@id\"}" +
                "}}");
        assertEquals("http://purl.obolibrary.org/obo/", prefixes.get("obo:"));
        assertNull(prefixes.get("eats:"));
    }

    @Test
    public void keywordAliasesAreNotPrefixes() throws Exception {
        assertTrue(read("{\"@context\": {\"id\": \"@id\", \"type\": \"@type\"}}").isEmpty());
    }

    @Test
    public void vocabBecomesTheDefaultPrefix() throws Exception {
        Map<String, String> prefixes = read("{\"@context\": {\"@vocab\": \"http://example.org/zoo#\"}}");
        assertEquals("http://example.org/zoo#", prefixes.get(JsonLdPrefixReader.DEFAULT_PREFIX_NAME));
    }

    @Test
    public void contextDoesNotHaveToComeFirst() throws Exception {
        Map<String, String> prefixes = read("{\"@graph\": [{\"@id\": \"x\", \"p\": {\"q\": [1, 2]}}], " +
                                            "\"@context\": {\"ex\": \"http://example.org/\"}}");
        assertEquals("http://example.org/", prefixes.get("ex:"));
    }

    @Test
    public void contextCanBeAList() throws Exception {
        Map<String, String> prefixes = read("{\"@context\": [\"http://remote.example/context.jsonld\", " +
                                            "{\"ex\": \"http://example.org/\"}]}");
        assertEquals("http://example.org/", prefixes.get("ex:"));
        assertEquals(1, prefixes.size());
    }

    @Test
    public void expandedDocumentsHaveNoPrefixes() throws Exception {
        assertTrue(read("[{\"@id\": \"http://example.org/a\"}]").isEmpty());
    }

    @Test
    public void readsLocalFiles() throws Exception {
        File file = folder.newFile("zoo.jsonld");
        Files.write(file.toPath(), "{\"@context\": {\"ex\": \"http://example.org/zoo#\"}}".getBytes(StandardCharsets.UTF_8));
        assertEquals("http://example.org/zoo#", JsonLdPrefixReader.readPrefixes(IRI.create(file), IS_ENTITY).get("ex:"));
    }

    @Test
    public void brokenOrMissingFilesGiveNoPrefixes() throws Exception {
        File broken = folder.newFile("broken.jsonld");
        Files.write(broken.toPath(), "{\"@context\": {".getBytes(StandardCharsets.UTF_8));
        assertTrue(JsonLdPrefixReader.readPrefixes(IRI.create(broken), IS_ENTITY).isEmpty());
        assertTrue(JsonLdPrefixReader.readPrefixes(IRI.create(new File(folder.getRoot(), "missing.jsonld")), IS_ENTITY).isEmpty());
    }

    @Test
    public void neverReadsFromTheNetwork() {
        assertTrue(JsonLdPrefixReader.readPrefixes(IRI.create("http://example.org/zoo.jsonld"), IS_ENTITY).isEmpty());
    }
}
