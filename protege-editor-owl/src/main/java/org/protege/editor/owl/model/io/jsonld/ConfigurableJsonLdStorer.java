package org.protege.editor.owl.model.io.jsonld;

import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.rio.helpers.BasicWriterSettings;
import org.eclipse.rdf4j.rio.helpers.JSONLDSettings;
import org.semanticweb.owlapi.formats.RDFJsonLDDocumentFormat;
import org.semanticweb.owlapi.model.OWLDocumentFormat;
import org.semanticweb.owlapi.model.OWLDocumentFormatFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.semanticweb.owlapi.rio.RioStorer;

import javax.annotation.Nonnull;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

/**
 * Two-step JSON-LD storer subclassing OWL API's RioStorer.
 * Step 1: Serializes the ontology via OWL API/Rio with native settings and prefix preservation.
 * Step 2: Post-processes the JSON-LD AST using JsonLdPostProcessor (term derivation, @vocab, key ordering).
 */
public class ConfigurableJsonLdStorer extends RioStorer {

    private static final long serialVersionUID = 1L;

    public ConfigurableJsonLdStorer(@Nonnull OWLDocumentFormatFactory formatFactory) {
        super(formatFactory, new Resource[0]);
    }

    @Override
    public boolean canStoreOntology(OWLDocumentFormat format) {
        return format instanceof ConfigurableJsonLDDocumentFormat 
            || format instanceof RDFJsonLDDocumentFormat;
    }

    @Override
    protected void storeOntology(OWLOntology ontology, OutputStream os, OWLDocumentFormat format)
            throws OWLOntologyStorageException {

        JsonLdPreferences prefs = JsonLdPreferences.getInstance();

        // 1. Ensure format parameters reflect active preferences
        if (format instanceof ConfigurableJsonLDDocumentFormat) {
            ((ConfigurableJsonLDDocumentFormat) format).applyPreferences(prefs);
        } else {
            format.setParameter(JSONLDSettings.JSONLD_MODE, prefs.getDocumentForm().getRioMode());
            format.setParameter(JSONLDSettings.COMPACT_ARRAYS, prefs.isCompactArrays());
            format.setParameter(JSONLDSettings.USE_NATIVE_TYPES, prefs.isUseNativeTypes());
            format.setParameter(BasicWriterSettings.PRETTY_PRINT, prefs.isPrettyPrint());
            format.setParameter(BasicWriterSettings.INLINE_BLANK_NODES, prefs.getBlankNodeEmbedding() != BlankNodeEmbedding.REFERENCE);
            format.setParameter(BasicWriterSettings.XSD_STRING_TO_PLAIN_LITERAL, prefs.isOmitXsdStringTypes());
            format.setParameter(JSONLDSettings.USE_RDF_TYPE, prefs.isUseRdfType());
        }

        // 2. Step 1: Render through OWL API / Rio to in-memory buffer
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        super.storeOntology(ontology, buffer, format);

        // 3. Step 2: In-memory AST post-processing and formatting
        try {
            JsonLdPostProcessor.process(buffer.toByteArray(), ontology, prefs, os);
        } catch (IOException e) {
            throw new OWLOntologyStorageException("Error during JSON-LD post-processing", e);
        }
    }

    @Override
    protected void storeOntology(OWLOntology ontology, Writer writer, OWLDocumentFormat format)
            throws OWLOntologyStorageException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        storeOntology(ontology, buffer, format);
        try {
            writer.write(buffer.toString(StandardCharsets.UTF_8.name()));
            writer.flush();
        } catch (IOException e) {
            throw new OWLOntologyStorageException("Error writing JSON-LD to writer", e);
        }
    }
}
