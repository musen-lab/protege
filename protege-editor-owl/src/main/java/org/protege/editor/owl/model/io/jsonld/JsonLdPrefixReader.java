package org.protege.editor.owl.model.io.jsonld;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.semanticweb.owlapi.model.IRI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Reads the prefixes from the top-level @context of a JSON-LD file. OWL API's JSON-LD parser does not keep
 * prefixes, so Protégé uses this to keep them when a JSON-LD file is opened and saved again.
 */
public final class JsonLdPrefixReader {

    private static final Logger logger = LoggerFactory.getLogger(JsonLdPrefixReader.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** The key used for @vocab in the returned map: Protégé's default prefix. */
    public static final String DEFAULT_PREFIX_NAME = ":";

    private JsonLdPrefixReader() {
    }

    /**
     * Returns prefix name (ending in ':') to namespace, read from the document's @context.
     * Only local files are read; for any other document, or if the file can't be read, the result is empty.
     *
     * @param isEntityIri tells which IRIs are entities of the ontology; context entries that map to an entity
     *                    are short names, not prefixes
     */
    @Nonnull
    public static Map<String, String> readPrefixes(@Nonnull IRI documentIRI, @Nonnull Predicate<String> isEntityIri) {
        if (!"file".equalsIgnoreCase(documentIRI.getScheme())) {
            return new LinkedHashMap<>();
        }
        File file = new File(documentIRI.toURI());
        try (InputStream in = Files.newInputStream(file.toPath())) {
            return readPrefixes(in, isEntityIri);
        } catch (IOException | RuntimeException e) {
            logger.warn("Could not read the JSON-LD prefixes from {}: {}", documentIRI, e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    /**
     * Returns prefix name (ending in ':') to namespace, read from the top-level @context of a JSON-LD document.
     * Only the @context is read, not the rest of the document.
     * <p>
     * In a context, prefixes and short names look alike ("ex": "http://example.org/zoo#" versus
     * "age": "http://example.org/zoo#age"). An entry counts as a prefix when its value is an IRI that is not
     * an entity of the ontology, or when it is a definition with "@prefix": true. Short names written by
     * Protégé always map to entities, so they are never taken for prefixes, and namespaces such as
     * http://purl.obolibrary.org/obo/GO_ are kept even when nothing in the file uses them.
     * "@vocab" becomes the default prefix (":").
     *
     * @param isEntityIri tells which IRIs are entities of the ontology
     */
    @Nonnull
    public static Map<String, String> readPrefixes(@Nonnull InputStream in, @Nonnull Predicate<String> isEntityIri) throws IOException {
        Map<String, String> prefixes = new LinkedHashMap<>();
        try (JsonParser parser = new JsonFactory(MAPPER).createParser(in)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                // Expanded documents are arrays and have no @context
                return prefixes;
            }
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                String field = parser.getCurrentName();
                parser.nextToken();
                if ("@context".equals(field)) {
                    addPrefixes(parser.readValueAsTree(), isEntityIri, prefixes);
                    return prefixes;
                }
                parser.skipChildren();
            }
        }
        return prefixes;
    }

    private static void addPrefixes(JsonNode context, Predicate<String> isEntityIri, Map<String, String> prefixes) {
        if (context == null) {
            return;
        }
        if (context.isArray()) {
            for (JsonNode part : context) {
                addPrefixes(part, isEntityIri, prefixes);
            }
            return;
        }
        if (!context.isObject()) {
            // A reference to a remote context: never fetched
            return;
        }
        for (Iterator<Map.Entry<String, JsonNode>> it = context.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> e = it.next();
            String name = e.getKey();
            JsonNode value = e.getValue();
            if ("@vocab".equals(name)) {
                if (value.isTextual() && isIri(value.asText())) {
                    prefixes.put(DEFAULT_PREFIX_NAME, value.asText());
                }
                continue;
            }
            if (!JsonLdContextBuilder.isValidTerm(name)) {
                continue;
            }
            if (value.isTextual()) {
                String iri = value.asText();
                if (isIri(iri) && !isEntityIri.test(iri)) {
                    prefixes.put(name + ":", iri);
                }
            } else if (value.isObject() && value.path("@prefix").asBoolean(false) && value.path("@id").isTextual()) {
                String iri = value.get("@id").asText();
                if (isIri(iri)) {
                    prefixes.put(name + ":", iri);
                }
            }
        }
    }

    private static boolean isIri(String text) {
        return !text.startsWith("@") && text.indexOf(':') > 0;
    }
}
