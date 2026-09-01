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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

    /*
     * Functional syntax: 'Ontology(<iri>' opens the ontology frame; Manchester
     * syntax: 'Ontology: <iri>'. Same shapes the OWL API's AutoIRIMapper has
     * matched for years (its 'pattern' and 'manPattern'), except anchored with
     * find() rather than Matcher.matches(), so trailing content on the line
     * (a version IRI, an annotation) does not defeat the match. In both formats
     * an optional version IRI follows the ontology IRI, either on the same line
     * (second group) or alone on the following line (the OWL API writers'
     * layout, matched by BARE_IRI_LINE).
     */
    private static final Pattern FUNCTIONAL_ONTOLOGY = Pattern.compile(
            "^\\s*Ontology\\s*\\(\\s*<([^>]*)>(?:\\s+<([^>]*)>)?");

    private static final Pattern MANCHESTER_ONTOLOGY = Pattern.compile(
            "^\\s*Ontology:\\s*<([^>]*)>(?:\\s+<([^>]*)>)?");

    private static final Pattern BARE_IRI_LINE = Pattern.compile("^\\s*<([^>]*)>\\s*$");

    private static final Pattern[] FRAME_DECLARATIONS = {FUNCTIONAL_ONTOLOGY, MANCHESTER_ONTOLOGY};

    /*
     * Turtle: the OWL API's writer continues the ontology statement onto the
     * next line for the version ('<iri> rdf:type owl:Ontology ;' then
     * 'owl:versionIRI <viri> .'), so this is matched anywhere in a line while
     * the ontology statement is still open, not just at line start.
     */
    private static final Pattern TURTLE_VERSION = Pattern.compile(
            "(?:^|\\s)owl:versionIRI\\s+<([^>]*)>");

    /*
     * OBO: the header's 'ontology: <id>' tag carries no IRI; the OWL API derives
     * one by the OBO Foundry convention (purl prefix + id + ".owl", slashed
     * subset ids included), so the catalog must list the same IRI the loader
     * will assign. The .obo document URL is listed too, because OBO import
     * lines conventionally reference the document rather than the derived IRI.
     * Lowercase 'ontology:' cannot collide with Manchester's 'Ontology:'.
     */
    private static final Pattern OBO_ONTOLOGY = Pattern.compile("^ontology:\\s*(\\S+)\\s*$");

    /*
     * OBO: 'data-version: <v>' derives the version IRI by the same Foundry
     * convention, with the full id repeated: obo/<id>/<v>/<id>.owl (verified
     * against OWL API 4.5.29, slashed ids included).
     */
    private static final Pattern OBO_DATA_VERSION = Pattern.compile("^data-version:\\s*(\\S+)\\s*$");

    private static final String OBO_PURL_PREFIX = "http://purl.obolibrary.org/obo/";

    @Override
    public Set<URI> getSuggestions(File f) {
        Set<URI> suggestions = extractFromXml(f);
        if (suggestions.isEmpty()) {
            suggestions = extractFromTextHead(f);
        }
        if (suggestions.isEmpty()) {
            logger.debug("No ontology IRI could be extracted from {}", f);
        }
        return suggestions;
    }

    /*
     * Text-based formats (Turtle, functional syntax, Manchester syntax, OBO).
     * The head of the file is read once into memory; each format then examines
     * it with its own small method. First format to find a declaration wins.
     */
    private Set<URI> extractFromTextHead(File f) {
        List<String> head = readHead(f);
        Set<URI> suggestions = extractFromTurtle(head);
        if (suggestions.isEmpty()) {
            suggestions = extractFromFrameSyntax(head);
        }
        if (suggestions.isEmpty()) {
            suggestions = extractFromObo(head);
        }
        return suggestions;
    }

    private static List<String> readHead(File f) {
        List<String> head = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null && head.size() < MAX_LINES_TO_EXAMINE) {
                head.add(line);
            }
        }
        catch (Throwable t) {
            logger.debug("Could not read {} as text: {}", f, t.toString());
        }
        return head;
    }

    private static Set<URI> extractFromTurtle(List<String> head) {
        URI base = null;
        for (int i = 0; i < head.size(); i++) {
            String line = head.get(i);
            Matcher baseMatcher = TURTLE_BASE.matcher(line);
            if (baseMatcher.find()) {
                base = parseUri(baseMatcher.group(1));
                continue;
            }
            Matcher declaration = TURTLE_ONTOLOGY.matcher(line);
            if (declaration.find()) {
                URI ontologyIri = resolveOntologyIri(declaration.group(1), base);
                // A line ending in ';' continues the statement: the version, if
                // any, is on one of the following lines.
                URI versionIri = line.trim().endsWith(";")
                        ? turtleVersionInStatement(head, i + 1, base) : null;
                return collectSuggestions(ontologyIri, versionIri);
            }
        }
        return Collections.emptySet();
    }

    private static URI turtleVersionInStatement(List<String> head, int firstLine, URI base) {
        for (int i = firstLine; i < head.size(); i++) {
            String line = head.get(i);
            Matcher version = TURTLE_VERSION.matcher(line);
            if (version.find()) {
                return resolveOntologyIri(version.group(1), base);
            }
            if (line.trim().endsWith(".")) {
                return null; // statement closed without a version
            }
        }
        return null;
    }

    private static Set<URI> extractFromFrameSyntax(List<String> head) {
        for (int i = 0; i < head.size(); i++) {
            for (Pattern format : FRAME_DECLARATIONS) {
                Matcher declaration = format.matcher(head.get(i));
                if (declaration.find()) {
                    URI ontologyIri = resolveOntologyIri(declaration.group(1), null);
                    // The version is either the second IRI on the same line, or
                    // alone on the immediately following line (the OWL API
                    // writers' layout).
                    URI versionIri = declaration.group(2) != null
                            ? resolveOntologyIri(declaration.group(2), null)
                            : bareIriOnLine(head, i + 1);
                    return collectSuggestions(ontologyIri, versionIri);
                }
            }
        }
        return Collections.emptySet();
    }

    private static URI bareIriOnLine(List<String> head, int index) {
        if (index >= head.size()) {
            return null;
        }
        Matcher version = BARE_IRI_LINE.matcher(head.get(index));
        return version.find() ? resolveOntologyIri(version.group(1), null) : null;
    }

    private static Set<URI> extractFromObo(List<String> head) {
        String id = null;
        String dataVersion = null;
        for (String line : head) {
            if (line.startsWith("[")) {
                break; // first stanza ends the header
            }
            Matcher ontologyTag = OBO_ONTOLOGY.matcher(line);
            if (id == null && ontologyTag.find()) {
                id = ontologyTag.group(1);
                continue;
            }
            Matcher dataVersionTag = OBO_DATA_VERSION.matcher(line);
            if (dataVersion == null && dataVersionTag.find()) {
                dataVersion = dataVersionTag.group(1);
            }
        }
        return id != null ? oboSuggestions(id, dataVersion) : Collections.emptySet();
    }

    private static Set<URI> collectSuggestions(URI ontologyIri, URI versionIri) {
        Set<URI> suggestions = new TreeSet<>();
        if (ontologyIri != null) {
            suggestions.add(ontologyIri);
        }
        if (versionIri != null && versionIri.isAbsolute()) {
            suggestions.add(versionIri);
        }
        return suggestions.isEmpty() ? Collections.emptySet() : suggestions;
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
        if (!baseUsable) {
            return null;
        }
        URI resolved = base.resolve(declaredUri);
        // Resolution against an opaque base (urn:...) hands the relative reference
        // back unchanged; a catalog entry must name an absolute IRI or nothing.
        return resolved.isAbsolute() ? resolved : null;
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

    private static Set<URI> oboSuggestions(String oboId, String dataVersion) {
        Set<URI> suggestions = new TreeSet<>();
        URI ontologyIri = parseUri(OBO_PURL_PREFIX + oboId + ".owl");
        if (ontologyIri != null && ontologyIri.isAbsolute()) {
            suggestions.add(ontologyIri);
        }
        URI documentUrl = parseUri(OBO_PURL_PREFIX + oboId + ".obo");
        if (documentUrl != null && documentUrl.isAbsolute()) {
            suggestions.add(documentUrl);
        }
        if (dataVersion != null) {
            URI versionIri = parseUri(OBO_PURL_PREFIX + oboId + "/" + dataVersion + "/" + oboId + ".owl");
            if (versionIri != null && versionIri.isAbsolute()) {
                suggestions.add(versionIri);
            }
        }
        return suggestions.isEmpty() ? Collections.emptySet() : suggestions;
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

        private String declaredVersionIri;

        private boolean inOntologyElement = false;

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
                    // OWL/XML declares both IRIs as unqualified attributes on this element
                    declaredOntologyIri = atts.getValue("ontologyIRI");
                    declaredVersionIri = atts.getValue("versionIRI");
                    throw new ScanCompleteException();
                }
                // RDF/XML: owl:versionIRI, if any, is a child element of this one.
                inOntologyElement = true;
            }
            else if (inOntologyElement && OWL_NS.equals(uri) && "versionIRI".equals(localName)) {
                declaredVersionIri = atts.getValue(RDF_NS, "resource");
                throw new ScanCompleteException();
            }
            if (elementsExamined >= MAX_ELEMENTS_TO_EXAMINE) {
                throw new ScanCompleteException();
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            if (inOntologyElement && OWL_NS.equals(uri) && "Ontology".equals(localName)) {
                // Ontology element closed without a version IRI: nothing more to find.
                throw new ScanCompleteException();
            }
        }

        Set<URI> collectedSuggestions() {
            Set<URI> suggestions = new TreeSet<>();
            URI declared = resolveOntologyIri(declaredOntologyIri, xmlBase);
            if (declared != null) {
                suggestions.add(declared);
            }
            // Compatibility with XmlBaseAlgorithm, which indexed every file under its
            // raw xml:base, relative or not: keep emitting it whenever it differs
            // from the declared IRI, so no historical mapping disappears.
            if (xmlBase != null && !xmlBase.equals(declared)) {
                suggestions.add(xmlBase);
            }
            URI version = resolveOntologyIri(declaredVersionIri, xmlBase);
            if (version != null && !version.equals(declared)) {
                suggestions.add(version);
            }
            return suggestions.isEmpty() ? Collections.emptySet() : suggestions;
        }
    }
}
