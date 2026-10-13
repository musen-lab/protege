package org.protege.editor.owl.model.io.jsonld;

import org.semanticweb.owlapi.model.OWLDocumentFormat;
import org.semanticweb.owlapi.util.OWLDocumentFormatFactoryImpl;

import java.util.Collections;

/**
 * Registers JSON-LD's MIME type with OWL API and creates prefix-aware document formats.
 * Each format has its own prefix manager, so mappings from one ontology do not affect another.
 */
public class ConfigurableJsonLDDocumentFormatFactory extends OWLDocumentFormatFactoryImpl {

    private static final long serialVersionUID = 1L;

    /** Registers {@code application/ld+json} as the format's MIME type. */
    public ConfigurableJsonLDDocumentFormatFactory() {
        super(Collections.singletonList("application/ld+json"), true, "application/ld+json");
    }

    @Override
    public OWLDocumentFormat createFormat() {
        return new ConfigurableJsonLDDocumentFormat();
    }
}
