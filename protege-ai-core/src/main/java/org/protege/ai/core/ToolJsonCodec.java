package org.protege.ai.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.type.LogicalType;
import org.protege.ai.api.AgentToolDefinition;

import java.util.Objects;

/** JSON binding for one tool's declared Java types. Does not perform schema validation. */
final class ToolJsonCodec<I, O> {

    private final ObjectReader reader;
    private final ObjectWriter writer;
    private final Class<O> resultType;

    ToolJsonCodec(AgentToolDefinition<I, O> definition) {
        var mapper = JsonMapper.builder()
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
                .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES)
                .build();
        mapper.coercionConfigFor(LogicalType.Textual)
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
        reader = mapper.readerFor(definition.argumentType());
        writer = mapper.writerFor(definition.resultType());
        resultType = definition.resultType();
    }

    I readArguments(String input) throws InvalidInputException {
        try {
            I arguments = reader.readValue(input);
            if (arguments == null) {
                throw new InvalidInputException("Tool arguments must not be null");
            }
            return arguments;
        } catch (JsonProcessingException e) {
            throw new InvalidInputException("Arguments do not match the tool's declared input type", e);
        }
    }

    String writeResult(O result) throws JsonProcessingException {
        return writer.writeValueAsString(resultType.cast(Objects.requireNonNull(result, "Tool returned null")));
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
