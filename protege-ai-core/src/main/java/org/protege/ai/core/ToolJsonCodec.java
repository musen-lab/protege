package org.protege.ai.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.type.LogicalType;
import org.protege.ai.api.AgentToolDefinition;
import org.protege.ai.api.AgentToolSchemas;

import java.util.Objects;
import java.io.IOException;

/** Validates JSON and binds it to one tool's declared Java types. */
final class ToolJsonCodec<I, O> {

    private final ObjectReader reader;
    private final ObjectWriter writer;
    private final Class<O> resultType;
    private final AgentToolSchemas schemas;
    private final ObjectReader jsonReader;
    private final ToolJsonSchema inputSchema;
    private final ToolJsonSchema outputSchema;

    ToolJsonCodec(AgentToolDefinition<I, O> definition) {
        var mapper = JsonMapper.builder()
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                // JSON Schema treats 7 and 7.0 as integers; it rejects fractional values before binding.
                .enable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
                .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
                .build();
        mapper.coercionConfigFor(LogicalType.Textual)
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
        var generator = new ToolSchemaGenerator(mapper);
        schemas = new AgentToolSchemas(generator.generate(definition.argumentType()),
                generator.generate(definition.resultType()));
        inputSchema = new ToolJsonSchema(mapper, schemas.inputSchema());
        outputSchema = new ToolJsonSchema(mapper, schemas.outputSchema());
        jsonReader = mapper.readerFor(JsonNode.class);
        reader = mapper.readerFor(definition.argumentType());
        writer = mapper.writerFor(definition.resultType());
        resultType = definition.resultType();
    }

    AgentToolSchemas schemas() {
        return schemas;
    }

    I readArguments(String input) throws InvalidInputException {
        try {
            JsonNode value = jsonReader.readValue(input);
            if (value == null || value.isMissingNode()) {
                throw new InvalidInputException("Tool arguments must contain a JSON value");
            }
            inputSchema.applyInputDefaults(value);
            String violations = inputSchema.violations(value);
            if (!violations.isEmpty()) {
                throw new InvalidInputException("Invalid tool arguments: " + violations);
            }
            return reader.readValue(value);
        } catch (IOException e) {
            throw new InvalidInputException("Arguments do not match the tool's declared input type", e);
        }
    }

    String writeResult(O result) throws JsonProcessingException {
        String json = writer.writeValueAsString(resultType.cast(Objects.requireNonNull(result, "Tool returned null")));
        String violations = outputSchema.violations(jsonReader.readTree(json));
        if (!violations.isEmpty()) {
            throw new IllegalStateException("Tool result does not match its schema: " + violations);
        }
        return json;
    }

    static final class InvalidInputException extends Exception {
        InvalidInputException(String message) {
            super(message);
        }

        InvalidInputException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
