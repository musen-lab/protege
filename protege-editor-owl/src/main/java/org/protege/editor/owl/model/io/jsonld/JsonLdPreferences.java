package org.protege.editor.owl.model.io.jsonld;

import org.protege.editor.core.prefs.Preferences;
import org.protege.editor.core.prefs.PreferencesManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Singleton managing active global JSON-LD serialization preferences and persistence.
 * Backed by Protégé's PreferencesManager ("org.protege.editor.owl.jsonld").
 */
public class JsonLdPreferences {

    private static final Logger LOGGER = LoggerFactory.getLogger(JsonLdPreferences.class);

    public static final String PREFERENCES_KEY = "org.protege.editor.owl.jsonld";

    // Keys
    public static final String KEY_ACTIVE_PRESET = "activePreset";
    public static final String KEY_CONTEXT_SOURCE = "contextSource";
    public static final String KEY_CONTEXT_EMBEDDING = "contextEmbedding";
    public static final String KEY_INCLUDE_ONTOLOGY_PREFIXES = "includeOntologyPrefixes";
    public static final String KEY_PRUNE_UNUSED_PREFIXES = "pruneUnusedPrefixes";
    public static final String KEY_USE_VOCAB = "useVocab";
    public static final String KEY_GENERATE_TERMS_FOR = "generateTermsFor";
    public static final String KEY_TERM_NAME_SOURCE = "termNameSource";
    public static final String KEY_TERM_COLLISION_POLICY = "termCollisionPolicy";
    public static final String KEY_DOCUMENT_FORM = "documentForm";
    public static final String KEY_WRAP_IN_GRAPH_ARRAY = "wrapInGraphArray";
    public static final String KEY_COMPACT_ARRAYS = "compactArrays";
    public static final String KEY_KEY_ORDERING = "keyOrdering";
    public static final String KEY_PRETTY_PRINT = "prettyPrint";
    public static final String KEY_INDENT_SPACES = "indentSpaces";
    public static final String KEY_USE_NATIVE_TYPES = "useNativeTypes";
    public static final String KEY_OMIT_XSD_STRING_TYPES = "omitXsdStringTypes";
    public static final String KEY_TYPE_COERCION_MODE = "typeCoercionMode";
    public static final String KEY_USE_RDF_TYPE = "useRdfType";
    public static final String KEY_NULL_LANGUAGE_FOR_UNTAGGED = "nullLanguageForUntagged";
    public static final String KEY_USE_LANGUAGE_MAPS = "useLanguageMaps";
    public static final String KEY_BLANK_NODE_LABELING = "blankNodeLabeling";
    public static final String KEY_BLANK_NODE_EMBEDDING = "blankNodeEmbedding";
    public static final String KEY_RDF_LIST_HANDLING = "rdfListHandling";
    public static final String KEY_LIST_PROPERTY_OVERRIDES = "listPropertyOverrides";
    public static final String KEY_INCLUDE_IMPORTS_CLOSURE = "includeImportsClosure";

    // Defaults
    public static final JsonLdPreset DEFAULT_ACTIVE_PRESET = JsonLdPreset.WEB_FRIENDLY;
    public static final ContextSource DEFAULT_CONTEXT_SOURCE = ContextSource.GENERATED;
    public static final ContextEmbedding DEFAULT_CONTEXT_EMBEDDING = ContextEmbedding.INLINE;
    public static final boolean DEFAULT_INCLUDE_ONTOLOGY_PREFIXES = true;
    public static final boolean DEFAULT_PRUNE_UNUSED_PREFIXES = true;
    public static final boolean DEFAULT_USE_VOCAB = false;
    public static final GenerateTermsFor DEFAULT_GENERATE_TERMS_FOR = GenerateTermsFor.ALL_PROPERTIES;
    public static final TermNameSource DEFAULT_TERM_NAME_SOURCE = TermNameSource.LOCAL_NAME;
    public static final TermCollisionPolicy DEFAULT_TERM_COLLISION_POLICY = TermCollisionPolicy.PREFIXED_IRI;
    public static final DocumentForm DEFAULT_DOCUMENT_FORM = DocumentForm.COMPACTED;
    public static final boolean DEFAULT_WRAP_IN_GRAPH_ARRAY = true;
    public static final boolean DEFAULT_COMPACT_ARRAYS = true;
    public static final KeyOrdering DEFAULT_KEY_ORDERING = KeyOrdering.KEYWORDS_FIRST;
    public static final boolean DEFAULT_PRETTY_PRINT = true;
    public static final int DEFAULT_INDENT_SPACES = 2;
    public static final boolean DEFAULT_USE_NATIVE_TYPES = true;
    public static final boolean DEFAULT_OMIT_XSD_STRING_TYPES = true;
    public static final TypeCoercionMode DEFAULT_TYPE_COERCION_MODE = TypeCoercionMode.AUTO;
    public static final boolean DEFAULT_USE_RDF_TYPE = false;
    public static final boolean DEFAULT_NULL_LANGUAGE_FOR_UNTAGGED = true;
    public static final boolean DEFAULT_USE_LANGUAGE_MAPS = true;
    public static final BlankNodeLabeling DEFAULT_BLANK_NODE_LABELING = BlankNodeLabeling.DETERMINISTIC;
    public static final BlankNodeEmbedding DEFAULT_BLANK_NODE_EMBEDDING = BlankNodeEmbedding.EMBED_ONCE;
    public static final RdfListHandling DEFAULT_RDF_LIST_HANDLING = RdfListHandling.LIST_CONTAINER;
    public static final String DEFAULT_LIST_PROPERTY_OVERRIDES = "owl:unionOf,owl:intersectionOf,owl:oneOf";
    public static final boolean DEFAULT_INCLUDE_IMPORTS_CLOSURE = false;

    private static final JsonLdPreferences INSTANCE = new JsonLdPreferences();

    // Fields
    private JsonLdPreset activePreset;
    private ContextSource contextSource;
    private ContextEmbedding contextEmbedding;
    private boolean includeOntologyPrefixes;
    private boolean pruneUnusedPrefixes;
    private boolean useVocab;
    private GenerateTermsFor generateTermsFor;
    private TermNameSource termNameSource;
    private TermCollisionPolicy termCollisionPolicy;
    private DocumentForm documentForm;
    private boolean wrapInGraphArray;
    private boolean compactArrays;
    private KeyOrdering keyOrdering;
    private boolean prettyPrint;
    private int indentSpaces;
    private boolean useNativeTypes;
    private boolean omitXsdStringTypes;
    private TypeCoercionMode typeCoercionMode;
    private boolean useRdfType;
    private boolean nullLanguageForUntagged;
    private boolean useLanguageMaps;
    private BlankNodeLabeling blankNodeLabeling;
    private BlankNodeEmbedding blankNodeEmbedding;
    private RdfListHandling rdfListHandling;
    private String listPropertyOverrides;
    private boolean includeImportsClosure;

    private JsonLdPreferences() {
        load();
    }

    public static JsonLdPreferences getInstance() {
        return INSTANCE;
    }

    private Preferences getPreferences() {
        return PreferencesManager.getInstance().getApplicationPreferences(PREFERENCES_KEY);
    }

    /**
     * Loads preferences from storage or falls back to sensible defaults.
     */
    public synchronized void load() {
        Preferences prefs = getPreferences();
        this.activePreset = loadEnum(prefs, KEY_ACTIVE_PRESET, JsonLdPreset.class, DEFAULT_ACTIVE_PRESET);
        this.contextSource = loadEnum(prefs, KEY_CONTEXT_SOURCE, ContextSource.class, DEFAULT_CONTEXT_SOURCE);
        this.contextEmbedding = loadEnum(prefs, KEY_CONTEXT_EMBEDDING, ContextEmbedding.class, DEFAULT_CONTEXT_EMBEDDING);
        this.includeOntologyPrefixes = prefs.getBoolean(KEY_INCLUDE_ONTOLOGY_PREFIXES, DEFAULT_INCLUDE_ONTOLOGY_PREFIXES);
        this.pruneUnusedPrefixes = prefs.getBoolean(KEY_PRUNE_UNUSED_PREFIXES, DEFAULT_PRUNE_UNUSED_PREFIXES);
        this.useVocab = prefs.getBoolean(KEY_USE_VOCAB, DEFAULT_USE_VOCAB);
        this.generateTermsFor = loadEnum(prefs, KEY_GENERATE_TERMS_FOR, GenerateTermsFor.class, DEFAULT_GENERATE_TERMS_FOR);
        this.termNameSource = loadEnum(prefs, KEY_TERM_NAME_SOURCE, TermNameSource.class, DEFAULT_TERM_NAME_SOURCE);
        this.termCollisionPolicy = loadEnum(prefs, KEY_TERM_COLLISION_POLICY, TermCollisionPolicy.class, DEFAULT_TERM_COLLISION_POLICY);
        this.documentForm = loadEnum(prefs, KEY_DOCUMENT_FORM, DocumentForm.class, DEFAULT_DOCUMENT_FORM);
        this.wrapInGraphArray = prefs.getBoolean(KEY_WRAP_IN_GRAPH_ARRAY, DEFAULT_WRAP_IN_GRAPH_ARRAY);
        this.compactArrays = prefs.getBoolean(KEY_COMPACT_ARRAYS, DEFAULT_COMPACT_ARRAYS);
        this.keyOrdering = loadEnum(prefs, KEY_KEY_ORDERING, KeyOrdering.class, DEFAULT_KEY_ORDERING);
        this.prettyPrint = prefs.getBoolean(KEY_PRETTY_PRINT, DEFAULT_PRETTY_PRINT);
        this.indentSpaces = prefs.getInt(KEY_INDENT_SPACES, DEFAULT_INDENT_SPACES);
        this.useNativeTypes = prefs.getBoolean(KEY_USE_NATIVE_TYPES, DEFAULT_USE_NATIVE_TYPES);
        this.omitXsdStringTypes = prefs.getBoolean(KEY_OMIT_XSD_STRING_TYPES, DEFAULT_OMIT_XSD_STRING_TYPES);
        this.typeCoercionMode = loadEnum(prefs, KEY_TYPE_COERCION_MODE, TypeCoercionMode.class, DEFAULT_TYPE_COERCION_MODE);
        this.useRdfType = prefs.getBoolean(KEY_USE_RDF_TYPE, DEFAULT_USE_RDF_TYPE);
        this.nullLanguageForUntagged = prefs.getBoolean(KEY_NULL_LANGUAGE_FOR_UNTAGGED, DEFAULT_NULL_LANGUAGE_FOR_UNTAGGED);
        this.useLanguageMaps = prefs.getBoolean(KEY_USE_LANGUAGE_MAPS, DEFAULT_USE_LANGUAGE_MAPS);
        this.blankNodeLabeling = loadEnum(prefs, KEY_BLANK_NODE_LABELING, BlankNodeLabeling.class, DEFAULT_BLANK_NODE_LABELING);
        this.blankNodeEmbedding = loadEnum(prefs, KEY_BLANK_NODE_EMBEDDING, BlankNodeEmbedding.class, DEFAULT_BLANK_NODE_EMBEDDING);
        this.rdfListHandling = loadEnum(prefs, KEY_RDF_LIST_HANDLING, RdfListHandling.class, DEFAULT_RDF_LIST_HANDLING);
        this.listPropertyOverrides = prefs.getString(KEY_LIST_PROPERTY_OVERRIDES, DEFAULT_LIST_PROPERTY_OVERRIDES);
        this.includeImportsClosure = prefs.getBoolean(KEY_INCLUDE_IMPORTS_CLOSURE, DEFAULT_INCLUDE_IMPORTS_CLOSURE);
    }

    /**
     * Persists current preferences to storage.
     */
    public synchronized void save() {
        Preferences prefs = getPreferences();
        prefs.putString(KEY_ACTIVE_PRESET, activePreset.name());
        prefs.putString(KEY_CONTEXT_SOURCE, contextSource.name());
        prefs.putString(KEY_CONTEXT_EMBEDDING, contextEmbedding.name());
        prefs.putBoolean(KEY_INCLUDE_ONTOLOGY_PREFIXES, includeOntologyPrefixes);
        prefs.putBoolean(KEY_PRUNE_UNUSED_PREFIXES, pruneUnusedPrefixes);
        prefs.putBoolean(KEY_USE_VOCAB, useVocab);
        prefs.putString(KEY_GENERATE_TERMS_FOR, generateTermsFor.name());
        prefs.putString(KEY_TERM_NAME_SOURCE, termNameSource.name());
        prefs.putString(KEY_TERM_COLLISION_POLICY, termCollisionPolicy.name());
        prefs.putString(KEY_DOCUMENT_FORM, documentForm.name());
        prefs.putBoolean(KEY_WRAP_IN_GRAPH_ARRAY, wrapInGraphArray);
        prefs.putBoolean(KEY_COMPACT_ARRAYS, compactArrays);
        prefs.putString(KEY_KEY_ORDERING, keyOrdering.name());
        prefs.putBoolean(KEY_PRETTY_PRINT, prettyPrint);
        prefs.putInt(KEY_INDENT_SPACES, indentSpaces);
        prefs.putBoolean(KEY_USE_NATIVE_TYPES, useNativeTypes);
        prefs.putBoolean(KEY_OMIT_XSD_STRING_TYPES, omitXsdStringTypes);
        prefs.putString(KEY_TYPE_COERCION_MODE, typeCoercionMode.name());
        prefs.putBoolean(KEY_USE_RDF_TYPE, useRdfType);
        prefs.putBoolean(KEY_NULL_LANGUAGE_FOR_UNTAGGED, nullLanguageForUntagged);
        prefs.putBoolean(KEY_USE_LANGUAGE_MAPS, useLanguageMaps);
        prefs.putString(KEY_BLANK_NODE_LABELING, blankNodeLabeling.name());
        prefs.putString(KEY_BLANK_NODE_EMBEDDING, blankNodeEmbedding.name());
        prefs.putString(KEY_RDF_LIST_HANDLING, rdfListHandling.name());
        prefs.putString(KEY_LIST_PROPERTY_OVERRIDES, listPropertyOverrides);
        prefs.putBoolean(KEY_INCLUDE_IMPORTS_CLOSURE, includeImportsClosure);
    }

    /**
     * Resets all parameters to factory defaults (Web-friendly preset).
     */
    public synchronized void resetToDefaults() {
        applyPreset(DEFAULT_ACTIVE_PRESET);
    }

    /**
     * Applies a high-level preset configuration to all 25 parameters.
     */
    public synchronized void applyPreset(JsonLdPreset preset) {
        Objects.requireNonNull(preset, "preset must not be null");
        this.activePreset = preset;

        switch (preset) {
            case WEB_FRIENDLY:
                this.contextSource = ContextSource.GENERATED;
                this.contextEmbedding = ContextEmbedding.INLINE;
                this.includeOntologyPrefixes = true;
                this.pruneUnusedPrefixes = true;
                this.useVocab = false;
                this.generateTermsFor = GenerateTermsFor.ALL_PROPERTIES;
                this.termNameSource = TermNameSource.LOCAL_NAME;
                this.termCollisionPolicy = TermCollisionPolicy.PREFIXED_IRI;
                this.documentForm = DocumentForm.COMPACTED;
                this.wrapInGraphArray = true;
                this.compactArrays = true;
                this.keyOrdering = KeyOrdering.KEYWORDS_FIRST;
                this.prettyPrint = true;
                this.indentSpaces = 2;
                this.useNativeTypes = true;
                this.omitXsdStringTypes = true;
                this.typeCoercionMode = TypeCoercionMode.AUTO;
                this.useRdfType = false;
                this.nullLanguageForUntagged = true;
                this.useLanguageMaps = true;
                this.blankNodeLabeling = BlankNodeLabeling.DETERMINISTIC;
                this.blankNodeEmbedding = BlankNodeEmbedding.EMBED_ONCE;
                this.rdfListHandling = RdfListHandling.LIST_CONTAINER;
                this.listPropertyOverrides = DEFAULT_LIST_PROPERTY_OVERRIDES;
                this.includeImportsClosure = false;
                break;

            case ROUND_TRIP_SAFE:
                this.contextSource = ContextSource.GENERATED;
                this.contextEmbedding = ContextEmbedding.INLINE;
                this.includeOntologyPrefixes = true;
                this.pruneUnusedPrefixes = false;
                this.useVocab = false;
                this.generateTermsFor = GenerateTermsFor.NONE;
                this.termNameSource = TermNameSource.LOCAL_NAME;
                this.termCollisionPolicy = TermCollisionPolicy.PREFIXED_IRI;
                this.documentForm = DocumentForm.COMPACTED;
                this.wrapInGraphArray = true;
                this.compactArrays = false;
                this.keyOrdering = KeyOrdering.NONE;
                this.prettyPrint = true;
                this.indentSpaces = 2;
                this.useNativeTypes = false;
                this.omitXsdStringTypes = false;
                this.typeCoercionMode = TypeCoercionMode.NONE;
                this.useRdfType = true;
                this.nullLanguageForUntagged = false;
                this.useLanguageMaps = false;
                this.blankNodeLabeling = BlankNodeLabeling.PRESERVE;
                this.blankNodeEmbedding = BlankNodeEmbedding.REFERENCE;
                this.rdfListHandling = RdfListHandling.NATIVE_LIST;
                this.listPropertyOverrides = DEFAULT_LIST_PROPERTY_OVERRIDES;
                this.includeImportsClosure = false;
                break;

            case DIFF_FRIENDLY:
                this.contextSource = ContextSource.GENERATED;
                this.contextEmbedding = ContextEmbedding.INLINE;
                this.includeOntologyPrefixes = true;
                this.pruneUnusedPrefixes = true;
                this.useVocab = false;
                this.generateTermsFor = GenerateTermsFor.ALL_PROPERTIES;
                this.termNameSource = TermNameSource.LOCAL_NAME;
                this.termCollisionPolicy = TermCollisionPolicy.PREFIXED_IRI;
                this.documentForm = DocumentForm.COMPACTED;
                this.wrapInGraphArray = true;
                this.compactArrays = true;
                this.keyOrdering = KeyOrdering.KEYWORDS_FIRST;
                this.prettyPrint = true;
                this.indentSpaces = 2;
                this.useNativeTypes = true;
                this.omitXsdStringTypes = true;
                this.typeCoercionMode = TypeCoercionMode.AUTO;
                this.useRdfType = false;
                this.nullLanguageForUntagged = true;
                this.useLanguageMaps = true;
                this.blankNodeLabeling = BlankNodeLabeling.DETERMINISTIC;
                this.blankNodeEmbedding = BlankNodeEmbedding.EMBED_ONCE;
                this.rdfListHandling = RdfListHandling.LIST_CONTAINER;
                this.listPropertyOverrides = DEFAULT_LIST_PROPERTY_OVERRIDES;
                this.includeImportsClosure = false;
                break;

            case EXPANDED:
                this.contextSource = ContextSource.GENERATED;
                this.contextEmbedding = ContextEmbedding.NONE;
                this.includeOntologyPrefixes = false;
                this.pruneUnusedPrefixes = false;
                this.useVocab = false;
                this.generateTermsFor = GenerateTermsFor.NONE;
                this.termNameSource = TermNameSource.LOCAL_NAME;
                this.termCollisionPolicy = TermCollisionPolicy.PREFIXED_IRI;
                this.documentForm = DocumentForm.EXPANDED;
                this.wrapInGraphArray = false;
                this.compactArrays = false;
                this.keyOrdering = KeyOrdering.NONE;
                this.prettyPrint = true;
                this.indentSpaces = 2;
                this.useNativeTypes = false;
                this.omitXsdStringTypes = false;
                this.typeCoercionMode = TypeCoercionMode.NONE;
                this.useRdfType = true;
                this.nullLanguageForUntagged = false;
                this.useLanguageMaps = false;
                this.blankNodeLabeling = BlankNodeLabeling.GENERATE;
                this.blankNodeEmbedding = BlankNodeEmbedding.REFERENCE;
                this.rdfListHandling = RdfListHandling.NATIVE_LIST;
                this.listPropertyOverrides = DEFAULT_LIST_PROPERTY_OVERRIDES;
                this.includeImportsClosure = false;
                break;

            case CUSTOM:
                // Retains existing parameter values
                break;
        }
    }

    private static <E extends Enum<E>> E loadEnum(Preferences prefs, String key, Class<E> enumClass, E defaultValue) {
        String name = prefs.getString(key, null);
        if (name != null) {
            try {
                return Enum.valueOf(enumClass, name);
            } catch (IllegalArgumentException e) {
                LOGGER.warn("Unknown enum value '{}' for key '{}', falling back to default '{}'", name, key, defaultValue);
            }
        }
        return defaultValue;
    }

    // --- Getters & Setters ---

    public JsonLdPreset getActivePreset() { return activePreset; }
    public void setActivePreset(JsonLdPreset activePreset) { this.activePreset = activePreset; }

    public ContextSource getContextSource() { return contextSource; }
    public void setContextSource(ContextSource contextSource) { this.contextSource = contextSource; }

    public ContextEmbedding getContextEmbedding() { return contextEmbedding; }
    public void setContextEmbedding(ContextEmbedding contextEmbedding) { this.contextEmbedding = contextEmbedding; }

    public boolean isIncludeOntologyPrefixes() { return includeOntologyPrefixes; }
    public void setIncludeOntologyPrefixes(boolean includeOntologyPrefixes) { this.includeOntologyPrefixes = includeOntologyPrefixes; }

    public boolean isPruneUnusedPrefixes() { return pruneUnusedPrefixes; }
    public void setPruneUnusedPrefixes(boolean pruneUnusedPrefixes) { this.pruneUnusedPrefixes = pruneUnusedPrefixes; }

    public boolean isUseVocab() { return useVocab; }
    public void setUseVocab(boolean useVocab) { this.useVocab = useVocab; }

    public GenerateTermsFor getGenerateTermsFor() { return generateTermsFor; }
    public void setGenerateTermsFor(GenerateTermsFor generateTermsFor) { this.generateTermsFor = generateTermsFor; }

    public TermNameSource getTermNameSource() { return termNameSource; }
    public void setTermNameSource(TermNameSource termNameSource) { this.termNameSource = termNameSource; }

    public TermCollisionPolicy getTermCollisionPolicy() { return termCollisionPolicy; }
    public void setTermCollisionPolicy(TermCollisionPolicy termCollisionPolicy) { this.termCollisionPolicy = termCollisionPolicy; }

    public DocumentForm getDocumentForm() { return documentForm; }
    public void setDocumentForm(DocumentForm documentForm) { this.documentForm = documentForm; }

    public boolean isWrapInGraphArray() { return wrapInGraphArray; }
    public void setWrapInGraphArray(boolean wrapInGraphArray) { this.wrapInGraphArray = wrapInGraphArray; }

    public boolean isCompactArrays() { return compactArrays; }
    public void setCompactArrays(boolean compactArrays) { this.compactArrays = compactArrays; }

    public KeyOrdering getKeyOrdering() { return keyOrdering; }
    public void setKeyOrdering(KeyOrdering keyOrdering) { this.keyOrdering = keyOrdering; }

    public boolean isPrettyPrint() { return prettyPrint; }
    public void setPrettyPrint(boolean prettyPrint) { this.prettyPrint = prettyPrint; }

    public int getIndentSpaces() { return indentSpaces; }
    public void setIndentSpaces(int indentSpaces) { this.indentSpaces = indentSpaces; }

    public boolean isUseNativeTypes() { return useNativeTypes; }
    public void setUseNativeTypes(boolean useNativeTypes) { this.useNativeTypes = useNativeTypes; }

    public boolean isOmitXsdStringTypes() { return omitXsdStringTypes; }
    public void setOmitXsdStringTypes(boolean omitXsdStringTypes) { this.omitXsdStringTypes = omitXsdStringTypes; }

    public TypeCoercionMode getTypeCoercionMode() { return typeCoercionMode; }
    public void setTypeCoercionMode(TypeCoercionMode typeCoercionMode) { this.typeCoercionMode = typeCoercionMode; }

    public boolean isUseRdfType() { return useRdfType; }
    public void setUseRdfType(boolean useRdfType) { this.useRdfType = useRdfType; }

    public boolean isNullLanguageForUntagged() { return nullLanguageForUntagged; }
    public void setNullLanguageForUntagged(boolean nullLanguageForUntagged) { this.nullLanguageForUntagged = nullLanguageForUntagged; }

    public boolean isUseLanguageMaps() { return useLanguageMaps; }
    public void setUseLanguageMaps(boolean useLanguageMaps) { this.useLanguageMaps = useLanguageMaps; }

    public BlankNodeLabeling getBlankNodeLabeling() { return blankNodeLabeling; }
    public void setBlankNodeLabeling(BlankNodeLabeling blankNodeLabeling) { this.blankNodeLabeling = blankNodeLabeling; }

    public BlankNodeEmbedding getBlankNodeEmbedding() { return blankNodeEmbedding; }
    public void setBlankNodeEmbedding(BlankNodeEmbedding blankNodeEmbedding) { this.blankNodeEmbedding = blankNodeEmbedding; }

    public RdfListHandling getRdfListHandling() { return rdfListHandling; }
    public void setRdfListHandling(RdfListHandling rdfListHandling) { this.rdfListHandling = rdfListHandling; }

    public String getListPropertyOverrides() { return listPropertyOverrides; }
    public void setListPropertyOverrides(String listPropertyOverrides) { this.listPropertyOverrides = listPropertyOverrides; }

    public boolean isIncludeImportsClosure() { return includeImportsClosure; }
    public void setIncludeImportsClosure(boolean includeImportsClosure) { this.includeImportsClosure = includeImportsClosure; }
}
