package org.protege.editor.owl.model.io.jsonld;

import org.eclipse.rdf4j.rio.RDFFormat;
import org.semanticweb.owlapi.formats.AbstractRioRDFDocumentFormatFactory;
import org.semanticweb.owlapi.formats.RioRDFDocumentFormat;
import org.semanticweb.owlapi.model.OWLDocumentFormat;

/**
 * Factory for creating ConfigurableJsonLDDocumentFormat instances.
 */
public class ConfigurableJsonLDDocumentFormatFactory extends AbstractRioRDFDocumentFormatFactory {

    private static final long serialVersionUID = 1L;

    public ConfigurableJsonLDDocumentFormatFactory() {
        super(RDFFormat.JSONLD);
    }

    @Override
    public RioRDFDocumentFormat createFormat() {
        return new ConfigurableJsonLDDocumentFormat();
    }
}
