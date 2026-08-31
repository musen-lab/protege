package org.protege.editor.owl.model.library.folder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts the IRI(s) under which a local ontology document should be listed in a
 * folder catalog, without fully loading the ontology (T1, import management).
 *
 * XML documents (RDF/XML, OWL/XML) are examined with a bounded SAX parse. The
 * declared ontology IRI (an owl:Ontology element's rdf:about or ontologyIRI
 * attribute) is the primary suggestion. xml:base resolves a relative declaration,
 * and stands in as the only suggestion when no ontology IRI is declared. When a
 * declared IRI and a differing xml:base are both present, both are suggested, so
 * that catalogs and imports built against the historical xml:base-only behavior
 * (XmlBaseAlgorithm) keep resolving.
 */
public class OntologyIriExtractionAlgorithm implements Algorithm {

    private static final Logger logger = LoggerFactory.getLogger(OntologyIriExtractionAlgorithm.class);

    /*
     * RDF/XML puts owl:Ontology at or near the top of the document; a document
     * that deep in shows no ontology declaration is indexed by xml:base alone
     * rather than parsed to the end.
     */
    private static final int MAX_ELEMENTS_TO_EXAMINE = 1000;

    private static final String OWL_NS = "http://www.w3.org/2002/07/owl#";

    private static final String RDF_NS = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";

    /*
     * Text-based formats declare the ontology within the first lines of the
     * document; a bounded scan keeps huge data files cheap to reject.
     */
    private static final int MAX_LINES_TO_EXAMINE = 100;

    /*
     * Turtle: '@base <iri>' (or SPARQL-style 'BASE <iri>') sets the base URI;
     * the ontology declaration is '<iri> a owl:Ontology', where 'a' can also be
     * written rdf:type or as the full rdf:type IRI, and the object can be the
     * conventional owl:Ontology prefix form or the full OWL IRI. Prefixed
     * subjects (':onto a owl:Ontology') are out of scope for this bounded scan.
     */
    private static final Pattern TURTLE_BASE = Pattern.compile(
            "^\\s*(?:@base|BASE)\\s+<([^>]*)>", Pattern.CASE_INSENSITIVE);

    private static final Pattern TURTLE_ONTOLOGY = Pattern.compile(
            "^\\s*<([^>]*)>\\s+(?:a|rdf:type|<http://www\\.w3\\.org/1999/02/22-rdf-syntax-ns#type>)\\s+"
                    + "(?:owl:Ontology|<http://www\\.w3\\.org/2002/07/owl#Ontology>)\\s*[.;]");

    @Override
    public Set<URI> getSuggestions(File f) {
        Set<URI> suggestions = extractFromXml(f);
        if (suggestions.isEmpty()) {
            suggestions = extractFromTextHead(f);
        }
        return suggestions;
    }

    private Set<URI> extractFromTextHead(File f) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            URI base = null;
            String line;
            int linesExamined = 0;
            while ((line = reader.readLine()) != null && linesExamined++ < MAX_LINES_TO_EXAMINE) {
                Matcher baseMatcher = TURTLE_BASE.matcher(line);
                if (baseMatcher.find()) {
                    base = parseUri(baseMatcher.group(1));
                    continue;
                }
                Matcher ontologyMatcher = TURTLE_ONTOLOGY.matcher(line);
                if (ontologyMatcher.find()) {
                    URI iri = resolveOntologyIri(ontologyMatcher.group(1), base);
                    return iri != null ? Collections.singleton(iri) : Collections.emptySet();
                }
            }
        }
        catch (Throwable t) {
            logger.debug("Could not examine {} as a text-based ontology format: {}", f, t.toString());
        }
        return Collections.emptySet();
    }

    /**
     * Resolves a declared ontology IRI against a base URI. An empty declaration
     * ("this document") means exactly the base; URI.resolve("") cannot express
     * that, because it follows RFC 2396 and drops the base's last path segment.
     * Returns null when no absolute IRI can be produced.
     */
    private static URI resolveOntologyIri(String declared, URI base) {
        if (declared == null) {
            return null;
        }
        boolean baseUsable = base != null && base.isAbsolute();
        if (declared.isEmpty()) {
            return baseUsable ? base : null;
        }
        URI declaredUri = parseUri(declared);
        if (declaredUri == null) {
            return null;
        }
        if (declaredUri.isAbsolute()) {
            return declaredUri;
        }
        return baseUsable ? base.resolve(declaredUri) : null;
    }

    private static URI parseUri(String value) {
        try {
            return new URI(value);
        }
        catch (URISyntaxException e) {
            logger.debug("Ignoring malformed IRI '{}'", value);
            return null;
        }
    }

    private Set<URI> extractFromXml(File f) {
        XmlOntologyHandler handler = new XmlOntologyHandler();
        try (InputStream is = new FileInputStream(f)) {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            // A folder scan must never touch the network or the filesystem beyond f.
            // Best effort: not every SAX implementation recognizes every feature,
            // and hardening must not disable extraction on the ones that don't.
            trySetFeature(factory, "http://xml.org/sax/features/external-general-entities");
            trySetFeature(factory, "http://xml.org/sax/features/external-parameter-entities");
            trySetFeature(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd");
            SAXParser parser = factory.newSAXParser();
            parser.parse(is, handler);
        }
        catch (ScanCompleteException e) {
            // expected: the handler found what it needed or hit the element bound
        }
        catch (Throwable t) {
            // Not XML, or malformed XML: fall through with whatever was captured
            // before the failure (possibly nothing). Never a user-facing warning.
            logger.debug("Could not examine {} as XML: {}", f, t.toString());
        }
        return handler.collectedSuggestions();
    }

    private static void trySetFeature(SAXParserFactory factory, String feature) {
        try {
            factory.setFeature(feature, false);
        }
        catch (Exception e) {
            logger.debug("SAX parser does not support disabling {}", feature);
        }
    }

    /**
     * Thrown by the handler to stop the SAX parse early; never leaves this class.
     */
    private static class ScanCompleteException extends SAXException {
        ScanCompleteException() {
            super("Ontology declaration scan complete");
        }
    }

    private static class XmlOntologyHandler extends DefaultHandler {

        private URI xmlBase;

        private String declaredOntologyIri;

        private int elementsExamined = 0;

        @Override
        public void startElement(String uri, String localName, String qName, Attributes atts)
                throws SAXException {
            elementsExamined++;
            if (elementsExamined == 1) {
                String base = atts.getValue(XMLConstants.XML_NS_URI, "base");
                if (base != null) {
                    try {
                        xmlBase = new URI(base);
                    }
                    catch (java.net.URISyntaxException e) {
                        logger.debug("Ignoring malformed xml:base '{}'", base);
                    }
                }
            }
            if (OWL_NS.equals(uri) && "Ontology".equals(localName)) {
                declaredOntologyIri = atts.getValue(RDF_NS, "about");
                if (declaredOntologyIri == null) {
                    // OWL/XML declares the IRI as an unqualified attribute
                    declaredOntologyIri = atts.getValue("ontologyIRI");
                }
                throw new ScanCompleteException();
            }
            if (elementsExamined >= MAX_ELEMENTS_TO_EXAMINE) {
                throw new ScanCompleteException();
            }
        }

        Set<URI> collectedSuggestions() {
            Set<URI> suggestions = new TreeSet<>();
            URI declared = resolveOntologyIri(declaredOntologyIri, xmlBase);
            if (declared != null) {
                suggestions.add(declared);
            }
            if (xmlBase != null && xmlBase.isAbsolute() && !xmlBase.equals(declared)) {
                suggestions.add(xmlBase);
            }
            return suggestions.isEmpty() ? Collections.emptySet() : suggestions;
        }
    }
}
