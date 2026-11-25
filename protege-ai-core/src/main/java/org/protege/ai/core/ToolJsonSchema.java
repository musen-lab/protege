package org.protege.ai.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonNodePath;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.PathType;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.resource.ClasspathSchemaLoader;
import org.protege.ai.api.ToolFailure.Detail;

import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** A compiled schema and omission handling for one argument or result contract. */
final class ToolJsonSchema {

    private final JsonNode document;
    private final JsonSchema validator;

    ToolJsonSchema(ObjectMapper mapper, String schema) {
        try {
            document = mapper.readTree(schema);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid generated tool schema", e);
        }
        // Swing's context class loader cannot see resources private to this OSGi bundle.
        var factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012,
                builder -> builder.schemaLoaders(loaders -> loaders.add(
                        new ClasspathSchemaLoader(ToolJsonSchema.class::getClassLoader))));
        var meta = factory.getSchema(SchemaLocation.of("https://json-schema.org/draft/2020-12/schema"));
        var errors = meta.validate(document);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Invalid generated tool schema: " + errors);
        }
        // Draft 2020-12 treats formats as annotations unless assertions are enabled.
        validator = factory.getSchema(document, SchemaValidatorsConfig.builder()
                .formatAssertionsEnabled(true).pathType(PathType.JSON_POINTER).build());
        validator.initializeValidators();
        checkDefaults(document, new JsonNodePath(PathType.JSON_POINTER));
    }

    String violations(JsonNode value) {
        return details(value).stream().map(Detail::message).collect(Collectors.joining("; "));
    }

    List<Detail> details(JsonNode value) {
        return validator.validate(value).stream()
                .map(message -> new Detail(message.getInstanceLocation().toString(), message.getMessage()))
                .sorted(Comparator.comparing(Detail::field).thenComparing(Detail::message))
                .limit(3).toList();
    }

    void applyInputDefaults(JsonNode value) {
        applyDefaults(value, document, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private void applyDefaults(JsonNode value, JsonNode schema, Set<JsonNode> expanding) {
        if (value == null || value.isNull()) {
            return;
        }
        if (schema.has("$ref")) {
            applyDefaults(value, resolve(schema.get("$ref").asText()), expanding);
        }
        // The generator uses these combinations for references and nullable types.
        for (String combination : List.of("allOf", "anyOf")) {
            for (var branch : schema.path(combination)) {
                applyDefaults(value, branch, expanding);
            }
        }
        if (value instanceof ObjectNode object) {
            schema.path("properties").properties().forEach(property -> {
                String name = property.getKey();
                JsonNode propertySchema = property.getValue();
                JsonNode child = object.get(name);
                if (child == null && propertySchema.has("default")) {
                    if (!expanding.add(propertySchema)) {
                        throw new IllegalArgumentException("Recursive tool default for property '" + name + "'");
                    }
                    // Never share mutable default nodes between calls or with the cached schema.
                    child = propertySchema.get("default").deepCopy();
                    object.set(name, child);
                    applyDefaults(child, propertySchema, expanding);
                    expanding.remove(propertySchema);
                } else if (child != null) {
                    applyDefaults(child, propertySchema, expanding);
                } else if (!isRequired(schema, name)) {
                    // Optional properties without defaults have already been checked as nullable.
                    object.putNull(name);
                }
            });
        } else if (value.isArray() && schema.has("items")) {
            for (var item : value) {
                applyDefaults(item, schema.get("items"), expanding);
            }
        }
    }

    private boolean isRequired(JsonNode schema, String name) {
        for (var required : schema.path("required")) {
            if (name.equals(required.asText())) {
                return true;
            }
        }
        return false;
    }

    private JsonNode resolve(String reference) {
        if (reference.equals("#")) {
            return document;
        }
        if (!reference.startsWith("#/")) {
            throw new IllegalArgumentException("Unsupported tool schema reference: " + reference);
        }
        return document.at(reference.substring(1));
    }

    private void checkDefaults(JsonNode schema, JsonNodePath path) {
        if (schema.has("default")) {
            JsonNode value = schema.get("default").deepCopy();
            applyDefaults(value, schema, Collections.newSetFromMap(new IdentityHashMap<>()));
            var errors = validator.getSubSchema(path).validate(value);
            if (!errors.isEmpty()) {
                throw new IllegalArgumentException("Invalid tool default at " + path + ": " + errors);
            }
        }
        for (String keyword : List.of("properties", "$defs")) {
            schema.path(keyword).properties().forEach(entry ->
                    checkDefaults(entry.getValue(), path.append(keyword).append(entry.getKey())));
        }
        if (schema.has("items")) {
            checkDefaults(schema.get("items"), path.append("items"));
        }
        for (String keyword : List.of("allOf", "anyOf")) {
            for (int i = 0; i < schema.path(keyword).size(); i++) {
                checkDefaults(schema.path(keyword).get(i), path.append(keyword).append(i));
            }
        }
    }
}
