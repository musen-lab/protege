package org.protege.editor.owl.model.io.jsonld;

import org.semanticweb.owlapi.model.OWLDocumentFormat;
import org.semanticweb.owlapi.util.OWLDocumentFormatFactoryImpl;

import java.util.Collections;

/**
 * Factory for creating ConfigurableJsonLDDocumentFormat instances.
 */
public class ConfigurableJsonLDDocumentFormatFactory extends OWLDocumentFormatFactoryImpl {

    private static final long serialVersionUID = 1L;

    public ConfigurableJsonLDDocumentFormatFactory() {
        super(Collections.singletonList("application/ld+json"), true, "application/ld+json");
    }

    @Override
    public OWLDocumentFormat createFormat() {
        return new ConfigurableJsonLDDocumentFormat();
    }
}
