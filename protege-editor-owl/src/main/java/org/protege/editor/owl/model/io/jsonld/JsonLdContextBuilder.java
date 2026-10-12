package org.protege.editor.owl.model.io.jsonld;

import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.Imports;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

/**
 * Builds the JSON-LD @context for an ontology from the ontology's prefixes and the settings in
 * {@link JsonLdPreferences}: prefixes, @vocab, short names for entities, "@type": "@id" for object
 * properties, and "@container": "@language" for language maps.
 */
public class JsonLdContextBuilder {

    private static final String OWL = "http://www.w3.org/2002/07/owl#";

    private static final String RDFS = "http://www.w3.org/2000/01/rdf-schema#";

    /**
     * OWL and RDFS properties whose values are always IRIs or blank nodes in OWL files. With type coercion on,
     * they get "@type": "@id" too, so for example "rdfs:subClassOf": "ex:Animal" instead of {"@id": "ex:Animal"}.
     * Properties that can have literal values (such as owl:hasValue or owl:oneOf) are left out.
     */
    private static final List<String> IRI_VALUED_VOCABULARY = Arrays.asList(
            RDFS + "subClassOf", RDFS + "subPropertyOf", RDFS + "domain", RDFS + "range",
            OWL + "equivalentClass", OWL + "disjointWith", OWL + "complementOf",
            OWL + "onProperty", OWL + "onClass", OWL + "onDataRange", OWL + "onDatatype",
            OWL + "someValuesFrom", OWL + "allValuesFrom",
            OWL + "inverseOf", OWL + "equivalentProperty", OWL + "propertyDisjointWith",
            OWL + "sameAs", OWL + "differentFrom", OWL + "imports", OWL + "versionIRI",
            OWL + "intersectionOf", OWL + "unionOf", OWL + "disjointUnionOf", OWL + "members",
            OWL + "propertyChainAxiom", OWL + "hasKey", OWL + "withRestrictions",
            OWL + "annotatedSource", OWL + "annotatedProperty",
            OWL + "assertionProperty", OWL + "sourceIndividual", OWL + "targetIndividual");

    private final JsonLdPreferences prefs;

    // The prefix names put into the context by the last call to build()
    private final Set<String> prefixNames = new HashSet<>();

    public JsonLdContextBuilder(@Nonnull JsonLdPreferences prefs) {
        this.prefs = Objects.requireNonNull(prefs);
    }

    /**
     * Returns the @context as a map from term to definition, in a stable order.
     *
     * @param usedIris the IRIs that appear in the document, used to leave out unused prefixes
     * @throws OWLOntologyStorageException if two entities want the same short name and the clash policy is ERROR
     */
    @Nonnull
    public Map<String, Object> build(@Nonnull OWLOntology ontology,
                                     @Nonnull OWLDocumentFormat format,
                                     @Nonnull Set<String> usedIris) throws OWLOntologyStorageException {
        Map<String, Object> context = new LinkedHashMap<>();
        Map<String, String> prefixes = prefixes(format, usedIris);
        prefixNames.clear();
        prefixNames.addAll(prefixes.keySet());
        context.putAll(prefixes);

        if (prefs.isUseVocab()) {
            vocabIri(ontology).ifPresent(vocab -> context.put("@vocab", vocab));
        }

        Imports imports = Imports.fromBoolean(prefs.isIncludeImportsClosure());
        Set<String> objectProperties = iris(ontology.getObjectPropertiesInSignature(imports));
        Set<String> languageTagged = new HashSet<>();
        Set<String> otherValues = new HashSet<>();
        if (prefs.isUseLanguageMaps()) {
            annotationValueKinds(ontology, imports, languageTagged, otherValues);
        }
        Map<String, String> terms = shortNames(ontology, imports, prefixes.keySet());

        for (String iri : definedIris(ontology, imports)) {
            boolean coerce = objectProperties.contains(iri) && prefs.getTypeCoercionMode() == TypeCoercionMode.ID;
            String term = terms.get(iri);
            // A language map can only hold language-tagged text. Other values of the property need another key:
            // with a short name, they fall back to the property's (compact) IRI; without one, the language map
            // would be the property's only key, so then it is only used when every value is language-tagged.
            boolean languageMap = languageTagged.contains(iri) && (term != null || !otherValues.contains(iri));
            if (term == null && (coerce || languageMap)) {
                // No short name, but the property still needs a definition for coercion or language maps
                term = compactIri(iri, prefixes);
            }
            if (term == null || context.containsKey(term)) {
                continue;
            }
            if (coerce || languageMap) {
                Map<String, Object> definition = new LinkedHashMap<>();
                definition.put("@id", iri);
                if (coerce) {
                    definition.put("@type", "@id");
                }
                if (languageMap) {
                    definition.put("@container", "@language");
                }
                context.put(term, definition);
            } else {
                context.put(term, iri);
            }
        }

        if (prefs.getTypeCoercionMode() == TypeCoercionMode.ID) {
            for (String iri : IRI_VALUED_VOCABULARY) {
                String term = compactIri(iri, prefixes);
                if (usedIris.contains(iri) && !context.containsKey(term)) {
                    Map<String, Object> definition = new LinkedHashMap<>();
                    definition.put("@id", iri);
                    definition.put("@type", "@id");
                    context.put(term, definition);
                }
            }
        }
        return context;
    }

    /**
     * The names of the prefixes in the context made by the last call to {@link #build}, as opposed to short names
     * and other entries.
     */
    @Nonnull
    public Set<String> getPrefixNames() {
        return Collections.unmodifiableSet(new HashSet<>(prefixNames));
    }

    private Map<String, String> prefixes(@Nonnull OWLDocumentFormat format, @Nonnull Set<String> usedIris) {
        Map<String, String> prefixes = new TreeMap<>();
        if (!prefs.isIncludeOntologyPrefixes() || !format.isPrefixOWLOntologyFormat()) {
            return prefixes;
        }
        format.asPrefixOWLOntologyFormat().getPrefixName2PrefixMap().forEach((name, namespace) -> {
            String prefix = name.endsWith(":") ? name.substring(0, name.length() - 1) : name;
            if (prefix.isEmpty() || !isValidTerm(prefix)) {
                return;
            }
            if (namespace.startsWith(prefix + ":")) {
                // For example "urn" -> "urn:example:": JSON-LD would read this as referring to itself
                // ("cyclic IRI mapping"), so leave it out; its IRIs are written in full instead
                return;
            }
            if (prefs.isPruneUnusedPrefixes() && !isUsed(namespace, usedIris)) {
                return;
            }
            prefixes.put(prefix, namespace);
        });
        return prefixes;
    }

    private static boolean isUsed(@Nonnull String namespace, @Nonnull Set<String> usedIris) {
        for (String iri : usedIris) {
            if (iri.startsWith(namespace) && iri.length() > namespace.length()) {
                return true;
            }
        }
        return false;
    }

    private static Optional<String> vocabIri(@Nonnull OWLOntology ontology) {
        com.google.common.base.Optional<IRI> ontologyIri = ontology.getOntologyID().getOntologyIRI();
        if (!ontologyIri.isPresent()) {
            return Optional.empty();
        }
        String iri = ontologyIri.get().toString();
        return Optional.of(iri.endsWith("#") || iri.endsWith("/") ? iri : iri + "#");
    }

    /**
     * IRIs that may get a context entry (all properties, plus classes for ALL_ENTITIES), sorted so every save
     * gives the same context.
     */
    private SortedSet<String> definedIris(@Nonnull OWLOntology ontology, @Nonnull Imports imports) {
        SortedSet<String> result = new TreeSet<>();
        result.addAll(iris(ontology.getObjectPropertiesInSignature(imports)));
        result.addAll(iris(ontology.getDataPropertiesInSignature(imports)));
        result.addAll(iris(ontology.getAnnotationPropertiesInSignature(imports)));
        if (prefs.getGenerateTermsFor() == GenerateTermsFor.ALL_ENTITIES) {
            result.addAll(iris(ontology.getClassesInSignature(imports)));
        }
        return result;
    }

    /**
     * IRIs that should get a short name, depending on generateTermsFor.
     */
    private SortedSet<String> termCandidates(@Nonnull OWLOntology ontology, @Nonnull Imports imports) {
        SortedSet<String> result = new TreeSet<>();
        switch (prefs.getGenerateTermsFor()) {
            case ANNOTATION_PROPERTIES:
                result.addAll(iris(ontology.getAnnotationPropertiesInSignature(imports)));
                break;
            case ALL_PROPERTIES:
            case ALL_ENTITIES:
                result.addAll(definedIris(ontology, imports));
                break;
            default:
                break;
        }
        return result;
    }

    /**
     * Short names for entities, as a map from IRI to name. Names that are already prefix names are never used.
     * Clashes between entities are handled by the termCollisionPolicy setting.
     */
    private Map<String, String> shortNames(@Nonnull OWLOntology ontology,
                                           @Nonnull Imports imports,
                                           @Nonnull Set<String> prefixNames) throws OWLOntologyStorageException {
        // Group candidates by wanted name; IRIs within a group stay in IRI order
        Map<String, List<String>> byName = new TreeMap<>();
        for (String iri : termCandidates(ontology, imports)) {
            String name = termName(IRI.create(iri), ontology);
            if (name != null && !prefixNames.contains(name)) {
                byName.computeIfAbsent(name, n -> new ArrayList<>()).add(iri);
            }
        }

        Map<String, String> result = new HashMap<>();
        Set<String> taken = new HashSet<>(prefixNames);
        taken.addAll(byName.keySet());
        for (Map.Entry<String, List<String>> e : byName.entrySet()) {
            String name = e.getKey();
            List<String> iris = e.getValue();
            if (iris.size() == 1) {
                result.put(iris.get(0), name);
                continue;
            }
            switch (prefs.getTermCollisionPolicy()) {
                case FIRST_WINS:
                    result.put(iris.get(0), name);
                    break;
                case DISAMBIGUATE:
                    result.put(iris.get(0), name);
                    int suffix = 1;
                    for (String iri : iris.subList(1, iris.size())) {
                        while (taken.contains(name + "_" + suffix)) {
                            suffix++;
                        }
                        String numbered = name + "_" + suffix;
                        taken.add(numbered);
                        result.put(iri, numbered);
                    }
                    break;
                case ERROR:
                    throw new OWLOntologyStorageException(
                            "JSON-LD: these entities would all get the short name '" + name + "': " + iris
                                    + ". Change the name clash setting in Preferences > JSON-LD, or rename the entities.");
                case PREFIXED_IRI:
                default:
                    // None of them gets the short name
                    break;
            }
        }
        return result;
    }

    /**
     * Sorts annotation properties by their values: those with at least one language-tagged value go into
     * {@code languageTagged}, those with at least one other value (untagged or typed text, an IRI, an anonymous
     * individual) into {@code otherValues}. Looks at annotation assertions, ontology annotations and annotations
     * on axioms and on other annotations.
     */
    private static void annotationValueKinds(@Nonnull OWLOntology ontology, @Nonnull Imports imports,
                                             @Nonnull Set<String> languageTagged, @Nonnull Set<String> otherValues) {
        Collection<OWLOntology> ontologies = imports == Imports.INCLUDED ? ontology.getImportsClosure() : Collections.singleton(ontology);
        for (OWLOntology o : ontologies) {
            for (OWLAnnotation annotation : o.getAnnotations()) {
                sortAnnotation(annotation, languageTagged, otherValues);
            }
            for (OWLAxiom ax : o.getAxioms()) {
                if (ax instanceof OWLAnnotationAssertionAxiom) {
                    OWLAnnotationAssertionAxiom assertion = (OWLAnnotationAssertionAxiom) ax;
                    sortValue(assertion.getProperty(), assertion.getValue(), languageTagged, otherValues);
                }
                for (OWLAnnotation annotation : ax.getAnnotations()) {
                    sortAnnotation(annotation, languageTagged, otherValues);
                }
            }
        }
    }

    private static void sortAnnotation(OWLAnnotation annotation, Set<String> languageTagged, Set<String> otherValues) {
        sortValue(annotation.getProperty(), annotation.getValue(), languageTagged, otherValues);
        for (OWLAnnotation nested : annotation.getAnnotations()) {
            sortAnnotation(nested, languageTagged, otherValues);
        }
    }

    private static void sortValue(OWLAnnotationProperty property, OWLAnnotationValue value,
                                  Set<String> languageTagged, Set<String> otherValues) {
        String iri = property.getIRI().toString();
        if (value instanceof OWLLiteral && ((OWLLiteral) value).hasLang()) {
            languageTagged.add(iri);
        } else {
            otherValues.add(iri);
        }
    }

    private static Set<String> iris(@Nonnull Set<? extends OWLEntity> entities) {
        Set<String> result = new HashSet<>();
        for (OWLEntity entity : entities) {
            result.add(entity.getIRI().toString());
        }
        return result;
    }

    /**
     * The wanted short name, or null if no valid name can be made. Label names fall back to the local name.
     */
    @Nullable
    private String termName(@Nonnull IRI iri, @Nonnull OWLOntology ontology) {
        if (prefs.getTermNameSource() == TermNameSource.RDFS_LABEL) {
            String label = labelName(iri, ontology);
            if (label != null) {
                return label;
            }
        }
        String localName = iri.getShortForm();
        return isValidTerm(localName) ? localName : null;
    }

    /**
     * The rdfs:label with no language tag or tagged "en", with spaces replaced by '_'; the first one in sorted
     * order, so every save picks the same label.
     */
    @Nullable
    private static String labelName(@Nonnull IRI iri, @Nonnull OWLOntology ontology) {
        SortedSet<String> labels = new TreeSet<>();
        for (OWLAnnotationAssertionAxiom ax : ontology.getAnnotationAssertionAxioms(iri)) {
            if (ax.getProperty().isLabel() && ax.getValue() instanceof OWLLiteral) {
                OWLLiteral literal = (OWLLiteral) ax.getValue();
                if (!literal.hasLang() || literal.getLang().equalsIgnoreCase("en")) {
                    String label = literal.getLiteral().trim().replaceAll("\\s+", "_");
                    if (isValidTerm(label)) {
                        labels.add(label);
                    }
                }
            }
        }
        return labels.isEmpty() ? null : labels.first();
    }

    private static String compactIri(@Nonnull String iri, @Nonnull Map<String, String> prefixes) {
        String best = null;
        String bestNamespace = "";
        for (Map.Entry<String, String> e : prefixes.entrySet()) {
            String namespace = e.getValue();
            if (iri.startsWith(namespace) && iri.length() > namespace.length() && namespace.length() > bestNamespace.length()) {
                best = e.getKey() + ":" + iri.substring(namespace.length());
                bestNamespace = namespace;
            }
        }
        return best != null ? best : iri;
    }

    /**
     * A usable JSON-LD term: not empty, no whitespace or ':', and not starting with '@'.
     */
    static boolean isValidTerm(@Nullable String name) {
        if (name == null || name.isEmpty() || name.startsWith("@") || name.contains(":")) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            if (Character.isWhitespace(name.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
