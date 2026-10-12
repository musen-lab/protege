package org.protege.editor.owl.model.io;

import org.semanticweb.owlapi.io.OWLOntologyDocumentSource;
import org.semanticweb.owlapi.io.OWLParser;
import org.semanticweb.owlapi.io.OWLParserException;
import org.semanticweb.owlapi.io.OWLParserFactory;
import org.semanticweb.owlapi.model.*;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * Wraps OWL API's RDF/JSON parser so that, when it fails on a document, the failure is reported as an
 * {@link OWLParserException}. OWL API then goes on to try the next parser.
 * <p>
 * When the format of a document is not known, OWL API tries its parsers one after the other, and the RDF/JSON
 * parser comes before the JSON-LD parser. Given a compacted JSON-LD document, the RDF/JSON parser throws an
 * IllegalArgumentException (it reads "@context" as a subject IRI), which OWL API does not catch, so loading stops
 * and the JSON-LD parser is never tried.
 */
public class RdfJsonParserFactoryWrapper implements OWLParserFactory {

    private static final long serialVersionUID = 1L;

    private final OWLParserFactory delegate;

    public RdfJsonParserFactoryWrapper(@Nonnull OWLParserFactory delegate) {
        this.delegate = Objects.requireNonNull(delegate);
    }

    @Override
    public OWLParser createParser() {
        return new Parser(delegate.createParser());
    }

    @Override
    public OWLParser get() {
        return createParser();
    }

    @Override
    public OWLDocumentFormatFactory getSupportedFormat() {
        return delegate.getSupportedFormat();
    }

    @Override
    public String getDefaultMIMEType() {
        return delegate.getDefaultMIMEType();
    }

    @Override
    public List<String> getMIMETypes() {
        return delegate.getMIMETypes();
    }

    @Override
    public boolean handlesMimeType(String mimeType) {
        return delegate.handlesMimeType(mimeType);
    }

    private static final class Parser implements OWLParser {

        private static final long serialVersionUID = 1L;

        private final OWLParser delegate;

        Parser(OWLParser delegate) {
            this.delegate = delegate;
        }

        @Override
        public OWLDocumentFormat parse(IRI documentIRI, OWLOntology ontology) throws IOException {
            try {
                return delegate.parse(documentIRI, ontology);
            } catch (OWLParserException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new OWLParserException("Not an RDF/JSON document: " + e.getMessage(), e);
            }
        }

        @Override
        public OWLDocumentFormat parse(OWLOntologyDocumentSource documentSource,
                                       OWLOntology ontology,
                                       OWLOntologyLoaderConfiguration configuration) throws IOException {
            try {
                return delegate.parse(documentSource, ontology, configuration);
            } catch (OWLParserException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new OWLParserException("Not an RDF/JSON document: " + e.getMessage(), e);
            }
        }

        @Override
        public String getName() {
            return delegate.getName();
        }

        @Override
        public OWLDocumentFormatFactory getSupportedFormat() {
            return delegate.getSupportedFormat();
        }
    }
}
