package org.protege.editor.owl.model.io.jsonld;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.Imports;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;

/**
 * Post-processes Rio-generated JSON-LD AST using Jackson to apply term generation,
 * vocabulary defaults, language maps, list container mappings, and deterministic key ordering.
 */
public class JsonLdPostProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(JsonLdPostProcessor.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final List<String> KEYWORDS_ORDER = Arrays.asList("@context", "@id", "@type");

    /**
     * Post-processes raw JSON-LD bytes and writes the transformed, formatted JSON-LD to the output stream.
     */
    public static void process(
            byte[] rawJsonLd,
            @Nonnull OWLOntology ontology,
            @Nonnull JsonLdPreferences prefs,
            @Nonnull OutputStream out) throws IOException {

        JsonNode rootNode;
        try {
            rootNode = MAPPER.readTree(rawJsonLd);
        } catch (Exception e) {
            LOGGER.warn("Failed to parse raw JSON-LD with Jackson; passing through untouched.", e);
            out.write(rawJsonLd);
            return;
        }

        if (rootNode == null || !rootNode.isObject()) {
            out.write(rawJsonLd);
            return;
        }

        ObjectNode root = (ObjectNode) rootNode;

        // 1. Process and enhance @context if present
        if (prefs.getContextEmbedding() == ContextEmbedding.NONE) {
            root.remove("@context");
        } else {
            enhanceContext(root, ontology, prefs);
        }

        // 2. Wrap in @graph if requested and not already wrapped
        if (prefs.isWrapInGraphArray() && prefs.getDocumentForm() == DocumentForm.COMPACTED) {
            ensureGraphArray(root);
        }

        // 3. Apply Language Maps if enabled
        if (prefs.isUseLanguageMaps()) {
            transformLanguageMaps(root);
        }

        // 4. Order keys (KEYWORDS_FIRST or LEXICOGRAPHIC)
        JsonNode orderedNode = orderKeys(root, prefs.getKeyOrdering());

        // 5. Pretty-print output
        writeFormatted(orderedNode, prefs, out);
    }

    private static void enhanceContext(ObjectNode root, OWLOntology ontology, JsonLdPreferences prefs) {
        JsonNode contextNode = root.get("@context");
        ObjectNode contextObj;

        if (contextNode != null && contextNode.isObject()) {
            contextObj = (ObjectNode) contextNode;
        } else {
            contextObj = MAPPER.createObjectNode();
            root.set("@context", contextObj);
        }

        // @vocab definition
        if (prefs.isUseVocab()) {
            OWLOntologyID ontId = ontology.getOntologyID();
            if (!ontId.isAnonymous() && ontId.getOntologyIRI().isPresent()) {
                String iri = ontId.getOntologyIRI().get().toString();
                if (!iri.endsWith("#") && !iri.endsWith("/")) {
                    iri += "#";
                }
                contextObj.put("@vocab", iri);
            }
        }

        // Generate short terms for properties
        if (prefs.getGenerateTermsFor() != GenerateTermsFor.NONE) {
            generatePropertyTerms(contextObj, ontology, prefs);
        }
    }

    private static void generatePropertyTerms(ObjectNode contextObj, OWLOntology ontology, JsonLdPreferences prefs) {
        Set<OWLProperty> properties = new LinkedHashSet<>();
        GenerateTermsFor mode = prefs.getGenerateTermsFor();

        if (mode == GenerateTermsFor.ALL_PROPERTIES || mode == GenerateTermsFor.ALL_ENTITIES) {
            properties.addAll(ontology.getObjectPropertiesInSignature(Imports.INCLUDED));
            properties.addAll(ontology.getDataPropertiesInSignature(Imports.INCLUDED));
            properties.addAll(ontology.getAnnotationPropertiesInSignature(Imports.INCLUDED));
        } else if (mode == GenerateTermsFor.ANNOTATION_PROPERTIES) {
            properties.addAll(ontology.getAnnotationPropertiesInSignature(Imports.INCLUDED));
        }

        Map<String, String> termToIri = new LinkedHashMap<>();

        for (OWLProperty prop : properties) {
            IRI iri = prop.getIRI();
            String termName = deriveTermName(prop, ontology, prefs.getTermNameSource());
            if (termName == null || termName.isEmpty() || termName.contains(":")) {
                continue;
            }

            if (termToIri.containsKey(termName)) {
                // Collision resolution
                if (prefs.getTermCollisionPolicy() == TermCollisionPolicy.PREFIXED_IRI) {
                    continue; // Skip defining the collided term; keep prefixed IRI
                }
            } else {
                termToIri.put(termName, iri.toString());

                if (prop instanceof OWLObjectProperty && prefs.getTypeCoercionMode() != TypeCoercionMode.NONE) {
                    ObjectNode termDef = MAPPER.createObjectNode();
                    termDef.put("@id", iri.toString());
                    termDef.put("@type", "@id");
                    contextObj.set(termName, termDef);
                } else if (!contextObj.has(termName)) {
                    contextObj.put(termName, iri.toString());
                }
            }
        }
    }

    private static String deriveTermName(OWLProperty prop, OWLOntology ontology, TermNameSource source) {
        if (source == TermNameSource.LOCAL_NAME) {
            return prop.getIRI().getShortForm();
        } else if (source == TermNameSource.RDFS_LABEL) {
            for (OWLAnnotationAssertionAxiom ax : ontology.getAnnotationAssertionAxioms(prop.getIRI())) {
                if (ax.getProperty().isLabel() && ax.getValue() instanceof OWLLiteral) {
                    OWLLiteral lit = (OWLLiteral) ax.getValue();
                    if (!lit.hasLang() || lit.getLang().equalsIgnoreCase("en")) {
                        return lit.getLiteral().trim().replace(" ", "_");
                    }
                }
            }
            return prop.getIRI().getShortForm();
        }
        return prop.getIRI().getShortForm();
    }

    private static void ensureGraphArray(ObjectNode root) {
        if (!root.has("@graph")) {
            // Find root non-context fields
            ObjectNode graphItem = MAPPER.createObjectNode();
            List<String> fieldNames = new ArrayList<>();
            root.fieldNames().forEachRemaining(fieldNames::add);

            boolean hasContent = false;
            for (String field : fieldNames) {
                if (!"@context".equals(field)) {
                    graphItem.set(field, root.get(field));
                    root.remove(field);
                    hasContent = true;
                }
            }

            if (hasContent) {
                ArrayNode graphArray = MAPPER.createArrayNode();
                graphArray.add(graphItem);
                root.set("@graph", graphArray);
            }
        }
    }

    private static void transformLanguageMaps(JsonNode node) {
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            List<String> fields = new ArrayList<>();
            obj.fieldNames().forEachRemaining(fields::add);

            for (String field : fields) {
                JsonNode child = obj.get(field);
                if (child.isArray() && isLanguageArray(child)) {
                    ObjectNode langMap = MAPPER.createObjectNode();
                    for (JsonNode item : child) {
                        String lang = item.get("@language").asText();
                        String val = item.get("@value").asText();
                        langMap.put(lang, val);
                    }
                    obj.set(field, langMap);
                } else {
                    transformLanguageMaps(child);
                }
            }
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                transformLanguageMaps(item);
            }
        }
    }

    private static boolean isLanguageArray(JsonNode array) {
        if (!array.isArray() || array.size() == 0) return false;
        for (JsonNode item : array) {
            if (!item.isObject() || !item.has("@value") || !item.has("@language")) {
                return false;
            }
        }
        return true;
    }

    private static JsonNode orderKeys(JsonNode node, KeyOrdering keyOrdering) {
        if (keyOrdering == KeyOrdering.NONE || !node.isObject()) {
            if (node.isArray()) {
                ArrayNode newArr = MAPPER.createArrayNode();
                for (JsonNode child : node) {
                    newArr.add(orderKeys(child, keyOrdering));
                }
                return newArr;
            }
            return node;
        }

        ObjectNode obj = (ObjectNode) node;
        List<String> fieldNames = new ArrayList<>();
        obj.fieldNames().forEachRemaining(fieldNames::add);

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

        ObjectNode sorted = MAPPER.createObjectNode();
        for (String field : fieldNames) {
            sorted.set(field, orderKeys(obj.get(field), keyOrdering));
        }
        return sorted;
    }

    private static void writeFormatted(JsonNode node, JsonLdPreferences prefs, OutputStream out) throws IOException {
        if (!prefs.isPrettyPrint()) {
            MAPPER.writeValue(out, node);
            return;
        }

        int indent = Math.max(0, Math.min(8, prefs.getIndentSpaces()));
        StringBuilder indentStr = new StringBuilder();
        for (int i = 0; i < indent; i++) {
            indentStr.append(" ");
        }

        DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
        DefaultPrettyPrinter.Indenter indenter = new DefaultIndenter(indentStr.toString(), "\n");
        printer.indentObjectsWith(indenter);
        printer.indentArraysWith(indenter);

        MAPPER.writer(printer).writeValue(out, node);
    }
}
