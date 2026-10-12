package org.protege.editor.owl.model.io.jsonld;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.jsonldjava.core.JsonLdConsts;
import com.github.jsonldjava.core.JsonLdError;
import com.github.jsonldjava.core.JsonLdOptions;
import com.github.jsonldjava.core.JsonLdProcessor;
import com.github.jsonldjava.utils.JsonUtils;

import javax.annotation.Nonnull;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.util.*;

/**
 * Turns the JSON-LD draft written by OWL API into the final document, and writes it out.
 * All jsonld-java calls are kept in this class.
 */
public final class JsonLdPostProcessor {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);

    private static final List<String> KEYWORDS_ORDER = Arrays.asList("@context", "@id", "@type");

    private static final String RDF_TYPE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type";

    private JsonLdPostProcessor() {
    }

    /**
     * Turns the draft into triples and back into expanded JSON-LD, applying useNativeTypes and useRdfType.
     * RDF lists come out as JSON-LD lists.
     */
    @Nonnull
    public static Object rebuild(@Nonnull byte[] draft, @Nonnull JsonLdPreferences prefs) throws IOException {
        try {
            JsonLdOptions toRdf = new JsonLdOptions();
            toRdf.format = JsonLdConsts.APPLICATION_NQUADS;
            Object nquads = JsonLdProcessor.toRDF(JsonUtils.fromInputStream(new ByteArrayInputStream(draft)), toRdf);
            JsonLdOptions fromRdf = new JsonLdOptions();
            fromRdf.format = JsonLdConsts.APPLICATION_NQUADS;
            fromRdf.setUseNativeTypes(prefs.isUseNativeTypes());
            fromRdf.setUseRdfType(prefs.isUseRdfType());
            Object expanded = JsonLdProcessor.fromRDF(nquads, fromRdf);
            if (!prefs.isUseRdfType()) {
                moveRdfTypeIntoTypeKeyword(expanded);
            }
            return expanded;
        } catch (JsonLdError e) {
            throw new IOException("JSON-LD processing failed: " + e.getMessage(), e);
        }
    }

    /**
     * Shapes the expanded document (compacted, flattened or expanded) with the given @context,
     * and applies the finishing options. Returns the document as a JSON tree.
     */
    @Nonnull
    public static JsonNode shapeAndFinish(@Nonnull Object expanded,
                                          @Nonnull Map<String, Object> context,
                                          @Nonnull JsonLdPreferences prefs) throws IOException {
        try {
            Object document = expanded;
            if (prefs.getBlankNodeEmbedding() == BlankNodeEmbedding.EMBED_ONCE
                    && prefs.getDocumentForm() != DocumentForm.FLATTENED) {
                document = nestBlankNodes(expanded);
            }
            return finish(MAPPER.valueToTree(shape(document, context, prefs)), prefs);
        } catch (JsonLdError e) {
            throw new IOException("JSON-LD processing failed: " + e.getMessage(), e);
        }
    }

    /**
     * All IRIs in an expanded document: property keys, @id values and @type values (node types and datatypes).
     * Blank node ids are left out.
     */
    @Nonnull
    public static Set<String> usedIris(@Nonnull Object expanded) {
        Set<String> iris = new HashSet<>();
        collectIris(expanded, iris);
        return iris;
    }

    private static void collectIris(Object value, Set<String> iris) {
        if (value instanceof List) {
            for (Object item : (List<?>) value) {
                collectIris(item, iris);
            }
        } else if (value instanceof Map) {
            for (Map.Entry<?, ?> e : ((Map<?, ?>) value).entrySet()) {
                String key = (String) e.getKey();
                if ("@id".equals(key) || "@type".equals(key)) {
                    addIris(e.getValue(), iris);
                } else if (!key.startsWith("@")) {
                    addIris(key, iris);
                    collectIris(e.getValue(), iris);
                } else if (!"@value".equals(key) && !"@language".equals(key)) {
                    collectIris(e.getValue(), iris);
                }
            }
        }
    }

    private static void addIris(Object value, Set<String> iris) {
        if (value instanceof String) {
            if (!((String) value).startsWith("_:")) {
                iris.add((String) value);
            }
        } else if (value instanceof List) {
            for (Object item : (List<?>) value) {
                addIris(item, iris);
            }
        }
    }

    /**
     * Puts each blank node that is referred to exactly once where it is referred to, and drops its blank node id.
     * Blank nodes that are referred to more than once, never referred to, or that are part of a loop stay
     * as separate nodes. Works on an expanded document (a list of node objects) and returns a new one.
     */
    @SuppressWarnings("unchecked")
    static Object nestBlankNodes(@Nonnull Object expanded) {
        if (!(expanded instanceof List)) {
            return expanded;
        }
        List<Object> nodes = (List<Object>) expanded;
        Map<String, Map<String, Object>> blankNodes = new LinkedHashMap<>();
        for (Object item : nodes) {
            if (item instanceof Map) {
                Object id = ((Map<?, ?>) item).get("@id");
                if (id instanceof String && ((String) id).startsWith("_:")) {
                    blankNodes.put((String) id, (Map<String, Object>) item);
                }
            }
        }
        Map<String, Integer> references = new HashMap<>();
        for (Object item : nodes) {
            countReferences(item, true, references);
        }

        Set<String> nested = new HashSet<>();
        List<Object> result = new ArrayList<>();
        for (Object item : nodes) {
            Object id = item instanceof Map ? ((Map<?, ?>) item).get("@id") : null;
            if (id instanceof String && references.getOrDefault(id, 0) == 1 && blankNodes.containsKey(id)) {
                // Will be placed where it is referred to, unless it turns out to be part of a loop
                continue;
            }
            result.add(nest(item, blankNodes, references, new HashSet<>(), nested, true));
        }
        // Blank nodes referred to once but only from within a loop of blank nodes: keep them as separate nodes
        for (Map.Entry<String, Map<String, Object>> e : blankNodes.entrySet()) {
            if (references.getOrDefault(e.getKey(), 0) == 1 && !nested.contains(e.getKey())) {
                result.add(e.getValue());
            }
        }
        return result;
    }

    /** Counts how often each blank node id is used as a value (not as the id of a node object). */
    private static void countReferences(Object value, boolean isNodeObject, Map<String, Integer> references) {
        if (value instanceof List) {
            for (Object item : (List<?>) value) {
                countReferences(item, false, references);
            }
        } else if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            Object id = map.get("@id");
            if (!isNodeObject && map.size() == 1 && id instanceof String && ((String) id).startsWith("_:")) {
                references.merge((String) id, 1, Integer::sum);
                return;
            }
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!"@id".equals(e.getKey())) {
                    countReferences(e.getValue(), false, references);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Object nest(Object value,
                               Map<String, Map<String, Object>> blankNodes,
                               Map<String, Integer> references,
                               Set<String> ancestors,
                               Set<String> nested,
                               boolean isNodeObject) {
        if (value instanceof List) {
            List<Object> copy = new ArrayList<>();
            for (Object item : (List<?>) value) {
                copy.add(nest(item, blankNodes, references, ancestors, nested, false));
            }
            return copy;
        }
        if (!(value instanceof Map)) {
            return value;
        }
        Map<String, Object> map = (Map<String, Object>) value;
        Object id = map.get("@id");
        if (!isNodeObject && map.size() == 1 && id instanceof String) {
            String blankId = (String) id;
            Map<String, Object> target = blankNodes.get(blankId);
            if (target != null && references.getOrDefault(blankId, 0) == 1 && !ancestors.contains(blankId)) {
                Set<String> path = new HashSet<>(ancestors);
                path.add(blankId);
                Map<String, Object> inlined = new LinkedHashMap<>();
                for (Map.Entry<String, Object> e : target.entrySet()) {
                    if (!"@id".equals(e.getKey())) {
                        inlined.put(e.getKey(), nest(e.getValue(), blankNodes, references, path, nested, false));
                    }
                }
                nested.add(blankId);
                return inlined;
            }
            return map;
        }
        Set<String> path = ancestors;
        if (id instanceof String && ((String) id).startsWith("_:")) {
            path = new HashSet<>(ancestors);
            path.add((String) id);
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : map.entrySet()) {
            copy.put(e.getKey(), "@id".equals(e.getKey())
                    ? e.getValue()
                    : nest(e.getValue(), blankNodes, references, path, nested, false));
        }
        return copy;
    }

    /**
     * jsonld-java keeps an rdf:type value as a normal property when that type is also described in the same
     * document. With useRdfType off, move such values into @type, so a node's types are all in one place.
     */
    @SuppressWarnings("unchecked")
    private static void moveRdfTypeIntoTypeKeyword(Object expanded) {
        if (!(expanded instanceof List)) {
            return;
        }
        for (Object item : (List<?>) expanded) {
            if (!(item instanceof Map)) {
                continue;
            }
            Map<String, Object> node = (Map<String, Object>) item;
            Object rdfTypes = node.get(RDF_TYPE);
            if (!(rdfTypes instanceof List)) {
                continue;
            }
            List<Object> kept = new ArrayList<>();
            List<Object> types = node.get("@type") instanceof List ? (List<Object>) node.get("@type") : new ArrayList<>();
            for (Object value : (List<?>) rdfTypes) {
                Object id = value instanceof Map ? ((Map<?, ?>) value).get("@id") : null;
                if (id instanceof String && ((Map<?, ?>) value).size() == 1) {
                    if (!types.contains(id)) {
                        types.add(id);
                    }
                } else {
                    kept.add(value);
                }
            }
            if (!types.isEmpty()) {
                node.put("@type", types);
            }
            if (kept.isEmpty()) {
                node.remove(RDF_TYPE);
            } else {
                node.put(RDF_TYPE, kept);
            }
        }
    }

    private static Object shape(@Nonnull Object expanded,
                                @Nonnull Map<String, Object> context,
                                @Nonnull JsonLdPreferences prefs) throws JsonLdError {
        JsonLdOptions options = new JsonLdOptions();
        options.setCompactArrays(prefs.isCompactArrays());
        Map<String, Object> contextDocument = Collections.singletonMap("@context", context);
        switch (prefs.getDocumentForm()) {
            case EXPANDED:
                return expanded;
            case FLATTENED:
                return JsonLdProcessor.flatten(expanded, contextDocument, options);
            case COMPACTED:
            default:
                return JsonLdProcessor.compact(expanded, contextDocument, options);
        }
    }

    private static JsonNode finish(@Nonnull JsonNode node, @Nonnull JsonLdPreferences prefs) {
        if (node.isObject()) {
            ObjectNode root = (ObjectNode) node;
            JsonNode context = root.get("@context");
            if (context != null && context.isObject() && context.size() == 0) {
                root.remove("@context");
            }
            // Compacted files always keep their nodes in @graph, even when there is only one
            if (prefs.getDocumentForm() == DocumentForm.COMPACTED) {
                ensureGraphArray(root);
            }
        }
        return orderKeys(node, prefs.getKeyOrdering(), true);
    }

    private static void ensureGraphArray(@Nonnull ObjectNode root) {
        if (root.has("@graph")) {
            return;
        }
        ObjectNode graphItem = MAPPER.createObjectNode();
        List<String> fieldNames = new ArrayList<>();
        root.fieldNames().forEachRemaining(fieldNames::add);
        for (String field : fieldNames) {
            if (!"@context".equals(field)) {
                graphItem.set(field, root.get(field));
                root.remove(field);
            }
        }
        ArrayNode graph = MAPPER.createArrayNode();
        if (graphItem.size() > 0) {
            graph.add(graphItem);
        }
        root.set("@graph", graph);
    }

    /**
     * Orders object keys. The root @context always comes first, whatever the setting.
     */
    private static JsonNode orderKeys(@Nonnull JsonNode node, @Nonnull KeyOrdering keyOrdering, boolean isRoot) {
        if (node.isArray()) {
            ArrayNode ordered = MAPPER.createArrayNode();
            for (JsonNode child : node) {
                ordered.add(orderKeys(child, keyOrdering, false));
            }
            return ordered;
        }
        if (!node.isObject()) {
            return node;
        }
        List<String> fieldNames = new ArrayList<>();
        node.fieldNames().forEachRemaining(fieldNames::add);
        if (keyOrdering == KeyOrdering.KEYWORDS_FIRST) {
            fieldNames.sort((k1, k2) -> {
                int idx1 = KEYWORDS_ORDER.indexOf(k1);
                int idx2 = KEYWORDS_ORDER.indexOf(k2);
                if (idx1 != -1 && idx2 != -1) return Integer.compare(idx1, idx2);
                if (idx1 != -1) return -1;
                if (idx2 != -1) return 1;
                return k1.compareTo(k2);
            });
        } else if (keyOrdering == KeyOrdering.LEXICOGRAPHIC) {
            Collections.sort(fieldNames);
        }
        if (isRoot && fieldNames.remove("@context")) {
            fieldNames.add(0, "@context");
        }
        ObjectNode ordered = MAPPER.createObjectNode();
        for (String field : fieldNames) {
            ordered.set(field, orderKeys(node.get(field), keyOrdering, false));
        }
        return ordered;
    }

    /**
     * Writes the document to the stream. The stream is flushed but not closed.
     */
    public static void write(@Nonnull JsonNode document, @Nonnull JsonLdPreferences prefs, @Nonnull OutputStream out) throws IOException {
        writer(prefs).writeValue(out, document);
        out.flush();
    }

    /**
     * Writes the document to the writer. The writer is flushed but not closed.
     */
    public static void write(@Nonnull JsonNode document, @Nonnull JsonLdPreferences prefs, @Nonnull Writer out) throws IOException {
        writer(prefs).writeValue(out, document);
        out.flush();
    }

    private static ObjectWriter writer(@Nonnull JsonLdPreferences prefs) {
        if (!prefs.isPrettyPrint()) {
            return MAPPER.writer();
        }
        char[] spaces = new char[prefs.getIndentSpaces()];
        Arrays.fill(spaces, ' ');
        DefaultPrettyPrinter.Indenter indenter = new DefaultIndenter(new String(spaces), "\n");
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
        printer.indentObjectsWith(indenter);
        printer.indentArraysWith(indenter);
        return MAPPER.writer(printer);
    }
}
