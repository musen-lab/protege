package org.protege.editor.owl.model;

import org.protege.editor.owl.model.io.jsonld.ConfigurableJsonLDDocumentFormat;
import org.protege.editor.owl.model.io.jsonld.JsonLdPrefixReader;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLDocumentFormat;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.parameters.Imports;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.function.Predicate;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Replaces loaded OWL API formats with Protégé's equivalents. For local JSON-LD files it also
 * recovers prefix mappings from the document context, which OWL API's parser does not retain.
 *
 * Matthew Horridge
 * Stanford Center for Biomedical Informatics Research
 * 8 Feb 2017
 */
public class DocumentFormatUpdater {

    private static final Logger logger = LoggerFactory.getLogger(DocumentFormatUpdater.class);

    @Nonnull
    private final DocumentFormatMapper formatMapper;

    public DocumentFormatUpdater(@Nonnull DocumentFormatMapper formatMapper) {
        this.formatMapper = checkNotNull(formatMapper);
    }

    /**
     * Updates the ontology's format after loading and restores local JSON-LD prefix mappings.
     *
     * @param ontology the loaded ontology whose format should be updated
     */
    public void updateFormat(@Nonnull OWLOntology ontology) {
        checkNotNull(ontology);
        OWLOntologyManager man = ontology.getOWLOntologyManager();
        OWLDocumentFormat format = man.getOntologyFormat(ontology);
        if(format == null) {
            return;
        }
        OWLDocumentFormat mappedFormat = formatMapper.mapFormat(format);
        if(mappedFormat != format && mappedFormat instanceof ConfigurableJsonLDDocumentFormat) {
            // The JSON-LD parser keeps no prefixes: read them back from the file's @context
            IRI documentIRI = man.getOntologyDocumentIRI(ontology);
            // Context entries that map to an entity of the ontology are short names, not prefixes
            Predicate<String> isEntityIri = iri -> ontology.containsEntityInSignature(IRI.create(iri), Imports.INCLUDED);
            JsonLdPrefixReader.readPrefixes(documentIRI, isEntityIri).forEach((name, namespace) -> {
                if(JsonLdPrefixReader.DEFAULT_PREFIX_NAME.equals(name)) {
                    ((ConfigurableJsonLDDocumentFormat) mappedFormat).setDefaultPrefix(namespace);
                }
                else {
                    ((ConfigurableJsonLDDocumentFormat) mappedFormat).setPrefix(name, namespace);
                }
            });
        }
        man.setOntologyFormat(ontology, mappedFormat);
        if(mappedFormat != format) {
            logger.info("Updated ontology {} document format class from: {} to: {}",
                        ontology.getOntologyID().getOntologyIRI(),
                        format.getClass().getName(),
                        mappedFormat.getClass().getName());
        }
    }
}
