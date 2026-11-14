package org.protege.ai.core;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.OptBoolean;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.victools.jsonschema.generator.FieldScope;
import com.github.victools.jsonschema.generator.CustomPropertyDefinition;
import com.github.victools.jsonschema.generator.SchemaGenerationContext;
import com.github.victools.jsonschema.generator.MemberScope;
import com.github.victools.jsonschema.generator.MethodScope;
import org.protege.ai.api.ToolNullable;

import java.lang.reflect.AnnotatedArrayType;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** The same property rules feed schema discovery and invocation validation. */
final class ToolPropertyRules {

    private ToolPropertyRules() {}

    /** Preserve type-use annotations at every list or array depth. */
    static CustomPropertyDefinition collectionSchema(MemberScope<?, ?> member, SchemaGenerationContext context) {
        if (!member.isContainerType()) {
            return null;
        }
        var schema = context.getGeneratorConfig().createObjectNode().put("type", "array");
        var item = member.asFakeContainerItemScope();
        var nested = collectionSchema(item, context);
        var itemSchema = nested == null ? context.createDefinitionReference(item.getType()) : nested.getValue();
        if (nullable(item)) {
            itemSchema = context.makeNullable(itemSchema);
        }
        schema.set("items", itemSchema);
        return new CustomPropertyDefinition(schema);
    }

    static boolean required(MemberScope<?, ?> member) {
        JsonProperty property = property(member);
        return property == null || property.isRequired() != OptBoolean.FALSE;
    }

    static boolean nullable(MemberScope<?, ?> member) {
        boolean allowed = nullableType(member, annotatedType(member));
        if (member instanceof FieldScope field && field.findGetter() != null) {
            allowed |= nullableType(member, annotatedType(field.findGetter()));
        } else if (member instanceof MethodScope method && method.findGetterField() != null) {
            allowed |= nullableType(member, annotatedType(method.findGetterField()));
        }
        if (allowed && member.getType().getErasedType().isPrimitive()) {
            throw invalid(member, "a primitive cannot be nullable; use its wrapper class");
        }
        return allowed;
    }

    static JsonNode defaultValue(MemberScope<?, ?> member, ObjectMapper mapper) {
        if (member.isFakeContainerItemScope()) {
            return null;
        }
        JsonProperty property = property(member);
        JsonNode value = null;
        if (property != null && !property.defaultValue().isEmpty()) {
            if (required(member)) {
                throw invalid(member, "a required property cannot declare a default; mark it optional or remove the default");
            }
            try {
                value = mapper.readTree(property.defaultValue());
                if (value == null || value.isMissingNode()) {
                    throw invalid(member, "defaultValue must contain a JSON value");
                }
            } catch (JsonProcessingException e) {
                throw invalid(member, "defaultValue must be valid JSON");
            }
        }
        if (!required(member) && value == null && !nullable(member)) {
            throw invalid(member, "an optional property needs a default or @ToolNullable");
        }
        return value;
    }

    private static JsonProperty property(MemberScope<?, ?> member) {
        return member.getAnnotationConsideringFieldAndGetter(JsonProperty.class);
    }

    private static AnnotatedType annotatedType(MemberScope<?, ?> member) {
        if (member.getRawMember() instanceof Field field) {
            return field.getAnnotatedType();
        }
        return ((Method) member.getRawMember()).getAnnotatedReturnType();
    }

    private static boolean nullableType(MemberScope<?, ?> member, AnnotatedType annotated) {
        var type = member.getDeclaredType();
        // The generator revisits a collection member for each nested item type.
        while (!type.equals(member.getType())) {
            if (annotated instanceof AnnotatedArrayType array) {
                annotated = array.getAnnotatedGenericComponentType();
                type = type.getArrayElementType();
            } else if (annotated instanceof AnnotatedParameterizedType parameterized
                    && type.getTypeParameters().size() == 1) {
                annotated = parameterized.getAnnotatedActualTypeArguments()[0];
                type = type.getTypeParameters().get(0);
            } else {
                return false;
            }
        }
        return annotated.isAnnotationPresent(ToolNullable.class);
    }

    private static IllegalArgumentException invalid(MemberScope<?, ?> member, String message) {
        return new IllegalArgumentException("Unsupported tool contract: property '" + member.getName() + "': " + message);
    }
}
