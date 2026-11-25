package org.protege.ai.core;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;

import java.io.IOException;
import java.util.Objects;

/** Maps class references to IRI strings. Schema validation checks their syntax. */
final class OwlClassJsonModule extends SimpleModule {

    OwlClassJsonModule(OWLDataFactory dataFactory) {
        super("AgentToolOWLClass");
        Objects.requireNonNull(dataFactory);
        addDeserializer(OWLClass.class, new JsonDeserializer<>() {
            @Override
            public OWLClass deserialize(JsonParser parser, DeserializationContext context) throws IOException {
                // Input validation has already required a string containing an absolute IRI.
                return dataFactory.getOWLClass(IRI.create(parser.getText()));
            }
        });
        addSerializer(OWLClass.class, new JsonSerializer<>() {
            @Override
            public void serialize(OWLClass value, JsonGenerator generator, SerializerProvider provider) throws IOException {
                generator.writeString(value.getIRI().toString());
            }
        });
    }
}
