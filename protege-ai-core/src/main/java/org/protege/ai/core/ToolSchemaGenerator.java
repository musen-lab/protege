package org.protege.ai.core;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonSerializable;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.github.victools.jsonschema.generator.CustomDefinition;
import com.github.victools.jsonschema.generator.MethodScope;
import com.github.victools.jsonschema.generator.Option;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.module.jackson.JacksonModule;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Generates schemas only for types and annotations with a defined JSON representation. */
final class ToolSchemaGenerator {

    private static final Set<Class<?>> SCALARS = Set.of(String.class, boolean.class, Boolean.class,
            byte.class, Byte.class, short.class, Short.class, int.class, Integer.class,
            long.class, Long.class, float.class, Float.class, double.class, Double.class,
            BigInteger.class, BigDecimal.class);

    private final ObjectMapper mapper;
    private final SchemaGenerator generator;

    ToolSchemaGenerator(ObjectMapper mapper) {
        this.mapper = mapper;
        var config = new SchemaGeneratorConfigBuilder(mapper, SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON)
                .with(new JacksonModule())
                .with(Option.GETTER_METHODS, Option.NONSTATIC_NONVOID_NONGETTER_METHODS,
                        Option.FIELDS_DERIVED_FROM_ARGUMENTFREE_METHODS,
                        Option.NULLABLE_ARRAY_ITEMS_ALLOWED,
                        Option.FORBIDDEN_ADDITIONAL_PROPERTIES_BY_DEFAULT);
        config.forFields().withRequiredCheck(ToolPropertyRules::required)
                .withCustomDefinitionProvider(ToolPropertyRules::collectionSchema)
                .withNullableCheck(ToolPropertyRules::nullable)
                .withDefaultResolver(field -> ToolPropertyRules.defaultValue(field, mapper));
        config.forMethods().withRequiredCheck(ToolPropertyRules::required)
                .withCustomDefinitionProvider(ToolPropertyRules::collectionSchema)
                .withNullableCheck(ToolPropertyRules::nullable)
                .withDefaultResolver(method -> ToolPropertyRules.defaultValue(method, mapper))
                .withIgnoreCheck(method -> getterProperty(method).isEmpty())
                .withPropertyNameOverrideResolver(method -> getterProperty(method)
                        .map(BeanPropertyDefinition::getName).orElse(null));
        config.forTypesInGeneral()
                .withNumberInclusiveMinimumResolver(type -> lowerBound(type.getType().getErasedType()))
                .withNumberInclusiveMaximumResolver(type -> upperBound(type.getType().getErasedType()));
        // Use the mapper's enum names, including individually renamed constants.
        config.forTypesInGeneral().withCustomDefinitionProvider((type, context) -> {
            Object[] constants = type.getErasedType().getEnumConstants();
            if (constants == null) {
                return null;
            }
            var schema = mapper.createObjectNode().put("type", "string");
            var values = schema.putArray("enum");
            Set<String> names = new HashSet<>();
            for (var value : constants) {
                String name = mapper.valueToTree(value).asText();
                if (!names.add(name)) {
                    throw unsupported("duplicate enum value '" + name + "' in " + type);
                }
                values.add(name);
            }
            if (names.isEmpty()) {
                throw unsupported("empty enum " + type);
            }
            return new CustomDefinition(schema);
        });
        generator = new SchemaGenerator(config.build());
    }

    private Optional<BeanPropertyDefinition> getterProperty(MethodScope method) {
        var type = mapper.constructType(method.getDeclaringType().getErasedType());
        return mapper.getSerializationConfig().introspect(type).findProperties().stream()
                .filter(property -> property.hasGetter()
                        && property.getGetter().getMember().equals(method.getRawMember()))
                .findFirst();
    }

    private static BigDecimal upperBound(Class<?> type) {
        if (type == byte.class || type == Byte.class) return BigDecimal.valueOf(Byte.MAX_VALUE);
        if (type == short.class || type == Short.class) return BigDecimal.valueOf(Short.MAX_VALUE);
        if (type == int.class || type == Integer.class) return BigDecimal.valueOf(Integer.MAX_VALUE);
        if (type == long.class || type == Long.class) return BigDecimal.valueOf(Long.MAX_VALUE);
        if (type == float.class || type == Float.class) return new BigDecimal(Float.toString(Float.MAX_VALUE));
        if (type == double.class || type == Double.class) return BigDecimal.valueOf(Double.MAX_VALUE);
        return null;
    }

    private static BigDecimal lowerBound(Class<?> type) {
        BigDecimal upper = upperBound(type);
        if (upper == null) return null;
        if (type == float.class || type == Float.class || type == double.class || type == Double.class) {
            return upper.negate();
        }
        return upper.negate().subtract(BigDecimal.ONE);
    }

    String generate(Class<?> type) {
        if (type.isPrimitive()) {
            throw unsupported("primitive root " + type.getName() + "; use its wrapper class");
        }
        checkType(mapper.constructType(type), new HashSet<>());
        return generator.generateSchema(type).toString();
    }

    private void checkType(JavaType type, Set<JavaType> checked) {
        if (!checked.add(type)) {
            return;
        }
        Class<?> raw = type.getRawClass();
        if (SCALARS.contains(raw)) {
            return;
        }
        if (raw == List.class || raw.isArray() && raw != byte[].class && raw != char[].class) {
            checkType(type.getContentType(), checked);
            return;
        }
        if (raw == byte[].class || raw == char[].class || raw == Object.class
                || raw.getName().startsWith("java.") || raw.isInterface()
                || JsonSerializable.class.isAssignableFrom(raw)
                || Modifier.isAbstract(raw.getModifiers())) {
            throw unsupported("type " + type.toCanonical());
        }
        for (Class<?> current = raw; current != null && current != Object.class && current != Record.class
                && current != Enum.class; current = current.getSuperclass()) {
            checkAnnotations(current);
            for (var field : current.getDeclaredFields()) {
                checkAnnotations(field);
            }
            for (var method : current.getDeclaredMethods()) {
                checkAnnotations(method);
            }
            for (var constructor : current.getDeclaredConstructors()) {
                checkAnnotations(constructor);
                for (var parameter : constructor.getParameters()) {
                    checkAnnotations(parameter);
                }
            }
        }
        if (!raw.isEnum()) {
            var description = mapper.getSerializationConfig().introspect(type);
            for (var property : description.findProperties()) {
                checkType(property.getPrimaryType(), checked);
            }
        }
    }

    private void checkAnnotations(AnnotatedElement element) {
        for (var annotation : element.getAnnotations()) {
            Class<?> type = annotation.annotationType();
            if (type.isAnnotationPresent(JacksonAnnotationsInside.class)) {
                throw unsupported("annotation bundle @" + type.getSimpleName() + " on " + element);
            }
            if (!type.getName().startsWith("com.fasterxml.jackson.")) {
                continue;
            }
            if (type == JsonProperty.class) {
                var property = (JsonProperty) annotation;
                if (property.access() != JsonProperty.Access.AUTO) {
                    throw unsupported("@JsonProperty access rules on " + element);
                }
            } else if (type != JsonPropertyDescription.class && type != JsonClassDescription.class) {
                throw unsupported("annotation @" + type.getSimpleName() + " on " + element);
            }
        }
    }

    private static IllegalArgumentException unsupported(String detail) {
        return new IllegalArgumentException("Unsupported tool contract: " + detail);
    }
}
