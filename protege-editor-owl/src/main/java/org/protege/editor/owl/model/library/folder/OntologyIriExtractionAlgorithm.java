package org.protege.editor.owl.model.library.folder;

import com.google.common.io.ByteStreams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the IRI (the web address) of the ontology stored in a local file, so the
 * folder catalog can point that address at the file. Reads only the start of the
 * file and never loads the ontology.
 * <p>
 * Handles RDF/XML, OWL/XML, Turtle, OWL functional syntax, Manchester syntax and
 * OBO. Returns the ontology IRI, the version IRI when the file states one, and,
 * for XML files, the {@code xml:base} value that older versions of the scan
 * indexed by. Returns nothing for a file it cannot read.
 */
public class OntologyIriExtractionAlgorithm implements PrioritizedAlgorithm {

    private static final Logger logger = LoggerFactory.getLogger(OntologyIriExtractionAlgorithm.class);

    // Stop parsing XML after this many elements. Real files declare the ontology near the top.
    private static final int MAX_ELEMENTS_TO_EXAMINE = 1000;

    /*
     * Never read more than this from a file, in any format. Counting lines or
     * elements is not enough: one huge line would still be read whole. XML files
     * declare the ontology near the top. Turtle written by tools that sort their
     * statements can put it anywhere, so the limit is generous: reading 8 MB of
     * Turtle takes well under a second, once per file.
     */
    private static final long MAX_BYTES_TO_EXAMINE = 8L * 1024L * 1024L;

    private static final String OWL_NS = "http://www.w3.org/2002/07/owl#";

    private static final String RDF_NS = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";

    /*
     * Turtle. '@base <iri>' or 'BASE <iri>' sets the base; '@prefix p: <iri>' or
     * 'PREFIX p: <iri>' declares a prefix. A statement starts with its subject and
     * ends with a period; lines in between start with a predicate. The ontology is
     * the subject of the statement that contains 'a owl:Ontology' ('a' may also be
     * written rdf:type or as its full IRI, owl:Ontology may be written in full).
     * Subjects and IRIs may be written in full, '<iri>', or as prefixed names,
     * 'ex:onto' or ':onto'.
     */
    private static final Pattern TURTLE_BASE = Pattern.compile(
            "^(?:@base|BASE)\\s+<([^>]*)>", Pattern.CASE_INSENSITIVE);

    private static final Pattern TURTLE_PREFIX = Pattern.compile(
            "^(?:@prefix|PREFIX)\\s+([A-Za-z0-9_.\\-]*):\\s*<([^>]*)>", Pattern.CASE_INSENSITIVE);

    private static final Pattern TURTLE_PREFIXED_NAME = Pattern.compile("^[A-Za-z0-9_.\\-]*:[^\\s;,]*");

    /*
     * 'a owl:Ontology'. The type may also come later in a comma-separated list, as
     * in 'a voaf:Vocabulary, owl:Ontology' or 'a <http://x.org/Thing>, owl:Ontology'.
     * Assumes the usual prefix names: 'rdf:' for the RDF namespace and 'owl:' for
     * OWL, which is how the OWL API, Protege and every surveyed file write them.
     */
    private static final Pattern TURTLE_ONTOLOGY_TYPE = Pattern.compile(
            "(?:^|\\s)(?:a|rdf:type|<http://www\\.w3\\.org/1999/02/22-rdf-syntax-ns#type>)\\s+"
                    + "(?:(?:<[^>]*>|[^\\s,;.]+)\\s*,\\s*)*"
                    + "(?:owl:Ontology|<http://www\\.w3\\.org/2002/07/owl#Ontology>)(?=[\\s;,.]|$)");

    /*
     * Functional syntax opens with 'Ontology(<iri>', Manchester syntax with
     * 'Ontology: <iri>'. A version IRI may follow on the same line (second group)
     * or alone on the next line. Matched with find(), so text after the IRI does
     * not break the match.
     */
    private static final Pattern FUNCTIONAL_ONTOLOGY = Pattern.compile(
            "^\\s*Ontology\\s*\\(\\s*<([^>]*)>(?:\\s+<([^>]*)>)?");

    private static final Pattern MANCHESTER_ONTOLOGY = Pattern.compile(
            "^\\s*Ontology:\\s*<([^>]*)>(?:\\s+<([^>]*)>)?");

    private static final Pattern BARE_IRI_LINE = Pattern.compile("^\\s*<([^>]*)>\\s*$");

    // Hand-written functional syntax often puts 'Ontology(' alone on its line, with the IRIs below.
    private static final Pattern FUNCTIONAL_ONTOLOGY_OPEN = Pattern.compile("^\\s*Ontology\\s*\\(\\s*$");

    private static final Pattern[] FRAME_DECLARATIONS = {FUNCTIONAL_ONTOLOGY, MANCHESTER_ONTOLOGY};

    // The version IRI is usually on its own line inside the ontology statement, so
    // it is matched anywhere in a line. Full IRI or prefixed name.
    private static final Pattern TURTLE_VERSION = Pattern.compile(
            "(?:^|\\s)owl:versionIRI\\s+(<[^>]*>|[A-Za-z0-9_.\\-]*:[^\\s;,]*)");

    /*
     * OBO. The 'ontology: <id>' header line has no IRI. The OWL API builds one
     * when it loads the file: purl prefix + id + '.owl'. The catalog must list
     * that same IRI, plus the '.obo' address, which OBO import lines often use.
     */
    private static final Pattern OBO_ONTOLOGY = Pattern.compile("^ontology:\\s*(\\S+)\\s*$");

    // OBO 'data-version: <v>' gives the version IRI obo/<id>/<v>/<id>.owl, as the OWL API builds it.
    private static final Pattern OBO_DATA_VERSION = Pattern.compile("^data-version:\\s*(\\S+)\\s*$");

    private static final String OBO_PURL_PREFIX = "http://purl.obolibrary.org/obo/";

    @Override
    public Set<URI> getSuggestions(File f) {
        Suggestions suggestions = getPrioritizedSuggestions(f);
        if (suggestions.secondary.isEmpty()) {
            return suggestions.primary;
        }
        Set<URI> all = new TreeSet<>(suggestions.primary);
        all.addAll(suggestions.secondary);
        return all;
    }

    /** IRIs read from the file are primary; IRIs built by convention (OBO) are secondary. */
    @Override
    public Suggestions getPrioritizedSuggestions(File f) {
        Set<URI> declared = extractFromXml(f);
        if (!declared.isEmpty()) {
            return new Suggestions(declared, Collections.emptySet());
        }
        Suggestions suggestions = extractFromTextHead(f);
        if (suggestions.isEmpty()) {
            logger.debug("No ontology IRI could be extracted from {}", f);
        }
        return suggestions;
    }

    // Text formats. Read the start of the file once, then let each format look at
    // it in turn. The first format that finds a declaration wins.
    private Suggestions extractFromTextHead(File f) {
        List<String> head = readHead(f);
        Set<URI> declared = extractFromTurtle(head);
        if (declared.isEmpty()) {
            declared = extractFromFrameSyntax(head);
        }
        if (!declared.isEmpty()) {
            return new Suggestions(declared, Collections.emptySet());
        }
        Set<URI> derived = extractFromObo(head);
        return derived.isEmpty() ? Suggestions.NONE : new Suggestions(Collections.emptySet(), derived);
    }

    private static List<String> readHead(File f) {
        List<String> head = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                ByteStreams.limit(new FileInputStream(f), MAX_BYTES_TO_EXAMINE), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (head.isEmpty() && !line.isEmpty() && line.charAt(0) == '\uFEFF') {
                    line = line.substring(1); // UTF-8 byte order mark, common from Windows editors
                }
                head.add(line);
            }
        }
        catch (IOException | RuntimeException e) {
            // Unreadable or undecodable text: no suggestions. Errors are not caught
            // (see extractFromXml).
            logger.debug("Could not read {} as text: {}", f, e.toString());
        }
        return head;
    }

    private static Set<URI> extractFromTurtle(List<String> rawHead) {
        // Strip strings and comments first: a look-alike inside a string must not be
        // indexed, and a trailing comment must not hide the ';' or '.' that ends a statement.
        List<String> head = TurtleCodeFilter.codeLines(rawHead);
        URI base = null;
        Map<String, String> prefixes = new HashMap<>();
        boolean inStatement = false;
        String subject = null;            // the current statement's subject, null when it is a blank node
        URI ontologyIri = null;           // set once the current statement says 'a owl:Ontology'
        URI versionIri = null;
        for (String rawLine : head) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (!inStatement) {
                Matcher baseMatcher = TURTLE_BASE.matcher(line);
                if (baseMatcher.find()) {
                    base = parseUri(baseMatcher.group(1));
                    continue;
                }
                Matcher prefixMatcher = TURTLE_PREFIX.matcher(line);
                if (prefixMatcher.find()) {
                    // A relative prefix IRI, such as <.> or <#>, is relative to the base.
                    URI namespace = resolveOntologyIri(prefixMatcher.group(2), base);
                    prefixes.put(prefixMatcher.group(1), namespace != null ? namespace.toString() : prefixMatcher.group(2));
                    continue;
                }
                inStatement = true;
                subject = subjectToken(line);
                if (subject != null) {
                    line = line.substring(subject.length());
                }
            }
            if (subject != null) {
                if (ontologyIri == null && TURTLE_ONTOLOGY_TYPE.matcher(line).find()) {
                    ontologyIri = turtleIri(subject, base, prefixes);
                }
                if (ontologyIri != null && versionIri == null) {
                    Matcher version = TURTLE_VERSION.matcher(line);
                    if (version.find()) {
                        versionIri = turtleIri(version.group(1), base, prefixes);
                    }
                }
            }
            if (line.endsWith(".")) {
                if (ontologyIri != null) {
                    return collectSuggestions(ontologyIri, versionIri);
                }
                inStatement = false;
                subject = null;
            }
        }
        return ontologyIri != null ? collectSuggestions(ontologyIri, versionIri) : Collections.emptySet();
    }

    // The token a statement starts with: '<iri>' or a prefixed name. Null for anything
    // else, such as a blank node '[' or '_:b1', whose statement is then skipped.
    private static String subjectToken(String line) {
        if (line.startsWith("<")) {
            int close = line.indexOf('>');
            return close > 0 ? line.substring(0, close + 1) : null;
        }
        if (line.startsWith("_:")) {
            return null;
        }
        Matcher name = TURTLE_PREFIXED_NAME.matcher(line);
        return name.find() ? name.group() : null;
    }

    // '<iri>' resolved against the base, or 'p:name' expanded with its prefix.
    private static URI turtleIri(String token, URI base, Map<String, String> prefixes) {
        if (token.startsWith("<")) {
            return resolveOntologyIri(token.substring(1, token.length() - 1), base);
        }
        int colon = token.indexOf(':');
        String namespace = prefixes.get(token.substring(0, colon));
        if (namespace == null) {
            return null;
        }
        String local = token.substring(colon + 1);
        while (local.endsWith(".")) {   // a period right after the name ends the statement
            local = local.substring(0, local.length() - 1);
        }
        return resolveOntologyIri(namespace + local, base);
    }

    private static Set<URI> extractFromFrameSyntax(List<String> head) {
        for (int i = 0; i < head.size(); i++) {
            if (FUNCTIONAL_ONTOLOGY_OPEN.matcher(head.get(i)).find()) {
                URI ontologyIri = bareIriOnLine(head, i + 1);
                URI versionIri = ontologyIri != null ? bareIriOnLine(head, i + 2) : null;
                return collectSuggestions(ontologyIri, versionIri);
            }
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

    /*
     * Resolves a declared IRI against a base. An empty declaration means "this
     * document", so it is the base itself; URI.resolve("") would drop the last part
     * of the base's path instead. Returns null when no absolute IRI results.
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
        try (InputStream is = ByteStreams.limit(new FileInputStream(f), MAX_BYTES_TO_EXAMINE)) {
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
        catch (IOException | SAXException | ParserConfigurationException | RuntimeException e) {
            // Not XML, malformed XML, or the byte cap cut the document short: fall
            // through with whatever was captured before the failure (possibly
            // nothing). Never a user-facing warning. Errors such as OutOfMemoryError
            // are deliberately not caught here: a folder scan must not hide them.
            logger.debug("Could not examine {} as XML: {}", f, e.toString());
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

    /** Stops the XML parse early, once the handler has what it needs. */
    private static class ScanCompleteException extends SAXException {
        ScanCompleteException() {
            super("Ontology declaration scan complete");
        }
    }

    private static class XmlOntologyHandler extends DefaultHandler {

        // The root element's xml:base, as written. Listed for compatibility with the older scan.
        private URI xmlBase;

        // Base IRI in effect at each open element. Entries are null when there is none.
        private final List<URI> baseScopes = new ArrayList<>();

        // Base IRI in effect at the owl:Ontology element.
        private URI declarationBase;

        private String declaredOntologyIri;

        private String declaredVersionIri;

        private boolean inOntologyElement = false;

        private int elementsExamined = 0;

        @Override
        public void startElement(String uri, String localName, String qName, Attributes atts)
                throws SAXException {
            elementsExamined++;
            URI inheritedBase = baseScopes.isEmpty() ? null : baseScopes.get(baseScopes.size() - 1);
            URI currentBase = inheritedBase;
            String baseAttribute = atts.getValue(XMLConstants.XML_NS_URI, "base");
            if (baseAttribute != null) {
                URI declaredBase = parseUri(baseAttribute);
                if (declaredBase != null) {
                    if (elementsExamined == 1) {
                        xmlBase = declaredBase;
                    }
                    // A relative xml:base on an inner element is itself resolved
                    // against the base inherited from its ancestors.
                    boolean canResolve = !declaredBase.isAbsolute()
                            && inheritedBase != null && inheritedBase.isAbsolute();
                    currentBase = canResolve ? inheritedBase.resolve(declaredBase) : declaredBase;
                }
            }
            baseScopes.add(currentBase);

            if (OWL_NS.equals(uri) && "Ontology".equals(localName)) {
                declarationBase = currentBase;
                declaredOntologyIri = atts.getValue(RDF_NS, "about");
                if (declaredOntologyIri == null) {
                    String id = atts.getValue(RDF_NS, "ID");
                    if (id != null) {
                        // rdf:ID="x" names the resource base#x (Protege 3-era files).
                        declaredOntologyIri = "#" + id;
                    }
                }
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
            if (!baseScopes.isEmpty()) {
                baseScopes.remove(baseScopes.size() - 1);
            }
            if (inOntologyElement && OWL_NS.equals(uri) && "Ontology".equals(localName)) {
                // Ontology element closed without a version IRI: nothing more to find.
                throw new ScanCompleteException();
            }
        }

        Set<URI> collectedSuggestions() {
            Set<URI> suggestions = new TreeSet<>();
            URI resolutionBase = declarationBase != null ? declarationBase : xmlBase;
            URI declared = resolveOntologyIri(declaredOntologyIri, resolutionBase);
            if (declared != null) {
                suggestions.add(declared);
            }
            // Compatibility with XmlBaseAlgorithm, which indexed every file under its
            // raw xml:base, relative or not: keep emitting it whenever it differs
            // from the declared IRI, so no historical mapping disappears.
            if (xmlBase != null && !xmlBase.equals(declared)) {
                suggestions.add(xmlBase);
            }
            URI version = resolveOntologyIri(declaredVersionIri, resolutionBase);
            if (version != null && !version.equals(declared)) {
                suggestions.add(version);
            }
            return suggestions.isEmpty() ? Collections.emptySet() : suggestions;
        }
    }
}
