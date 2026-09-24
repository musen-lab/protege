package org.protege.editor.owl.model.io.jsonld;

import org.semanticweb.owlapi.formats.RioRDFStorerFactory;
import org.semanticweb.owlapi.model.OWLStorer;
import org.semanticweb.owlapi.rio.AbstractRioStorerFactory;

/**
 * Factory for creating ConfigurableJsonLdStorer instances.
 */
public class ConfigurableJsonLdStorerFactory extends AbstractRioStorerFactory implements RioRDFStorerFactory {

    private static final long serialVersionUID = 1L;

    public ConfigurableJsonLdStorerFactory() {
        super(new ConfigurableJsonLDDocumentFormatFactory());
    }

    @Override
    public OWLStorer createStorer() {
        return new ConfigurableJsonLdStorer(getFormatFactory());
    }
}
