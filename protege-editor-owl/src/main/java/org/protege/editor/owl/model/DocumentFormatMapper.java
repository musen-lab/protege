package org.protege.editor.owl.model;

import org.protege.editor.owl.model.io.jsonld.ConfigurableJsonLDDocumentFormat;
import org.semanticweb.owlapi.formats.RDFJsonLDDocumentFormat;
import org.semanticweb.owlapi.formats.RioTurtleDocumentFormat;
import org.semanticweb.owlapi.formats.TurtleDocumentFormat;
import org.semanticweb.owlapi.model.OWLDocumentFormat;

import javax.annotation.Nonnull;

import static org.semanticweb.owlapi.util.OWLAPIPreconditions.checkNotNull;

/**
 * Maps OWL API formats to Protégé formats after loading. In particular, JSON-LD is mapped to a
 * prefix-aware format so recovered context mappings can be used by the next save.
 *
 * Matthew Horridge
 * Stanford Center for Biomedical Informatics Research
 * 8 Feb 2017
 */
public class DocumentFormatMapper {

    /**
     * Converts supported OWL API formats to their Protégé equivalents. The JSON-LD conversion
     * copies the add-missing-types setting and loader metadata; formats needing no conversion
     * are returned unchanged.
     *
     * @param format the format reported by OWL API
     * @return the format to attach to the loaded ontology
     */
    @Nonnull
    public OWLDocumentFormat mapFormat(@Nonnull OWLDocumentFormat format) {
        checkNotNull(format);
        if(format instanceof RioTurtleDocumentFormat) {
            RioTurtleDocumentFormat rioTurtleDocumentFormat = (RioTurtleDocumentFormat) format;
            TurtleDocumentFormat turtleDocumentFormat = new TurtleDocumentFormat();
            turtleDocumentFormat.copyPrefixesFrom(rioTurtleDocumentFormat);
            return turtleDocumentFormat;
        }
        if(format instanceof RDFJsonLDDocumentFormat && !(format instanceof ConfigurableJsonLDDocumentFormat)) {
            // OWL API's JSON-LD format keeps no prefixes; Protégé's does, so they survive the next save
            ConfigurableJsonLDDocumentFormat jsonLdFormat = new ConfigurableJsonLDDocumentFormat();
            jsonLdFormat.setAddMissingTypes(format.isAddMissingTypes());
            if(format.getOntologyLoaderMetaData() != null) {
                jsonLdFormat.setOntologyLoaderMetaData(format.getOntologyLoaderMetaData());
            }
            return jsonLdFormat;
        }
        return format;
    }
}
