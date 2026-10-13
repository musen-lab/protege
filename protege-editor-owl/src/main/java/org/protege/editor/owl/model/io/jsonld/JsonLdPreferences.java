package org.protege.editor.owl.model.io.jsonld;

import org.protege.editor.core.prefs.Preferences;
import org.protege.editor.core.prefs.PreferencesManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Typed model for JSON-LD save settings: context generation, output shape, literal handling,
 * blank node embedding, and imports scope. The application instance is stored through Protégé's
 * preferences under {@code org.protege.editor.owl.jsonld}; a new store can be supplied for tests.
 * Use {@link #getInstance()} for the settings Protégé saves with. Changes take effect for the next save;
 * call {@link #save()} to keep them across sessions. Defaults match {@link JsonLdPreset#WEB_FRIENDLY}.
 */
public class JsonLdPreferences {

    private static final Logger LOGGER = LoggerFactory.getLogger(JsonLdPreferences.class);

    public static final String PREFERENCES_KEY = "org.protege.editor.owl.jsonld";

    // Keys
    public static final String KEY_ACTIVE_PRESET = "activePreset";
    public static final String KEY_INCLUDE_ONTOLOGY_PREFIXES = "includeOntologyPrefixes";
    public static final String KEY_PRUNE_UNUSED_PREFIXES = "pruneUnusedPrefixes";
    public static final String KEY_USE_VOCAB = "useVocab";
    public static final String KEY_GENERATE_TERMS_FOR = "generateTermsFor";
    public static final String KEY_TERM_NAME_SOURCE = "termNameSource";
    public static final String KEY_TERM_COLLISION_POLICY = "termCollisionPolicy";
    public static final String KEY_DOCUMENT_FORM = "documentForm";
    public static final String KEY_COMPACT_ARRAYS = "compactArrays";
    public static final String KEY_KEY_ORDERING = "keyOrdering";
    public static final String KEY_PRETTY_PRINT = "prettyPrint";
    public static final String KEY_INDENT_SPACES = "indentSpaces";
    public static final String KEY_USE_NATIVE_TYPES = "useNativeTypes";
    public static final String KEY_TYPE_COERCION_MODE = "typeCoercionMode";
    public static final String KEY_USE_RDF_TYPE = "useRdfType";
    public static final String KEY_USE_LANGUAGE_MAPS = "useLanguageMaps";
    public static final String KEY_BLANK_NODE_EMBEDDING = "blankNodeEmbedding";
    public static final String KEY_INCLUDE_IMPORTS_CLOSURE = "includeImportsClosure";

    // Defaults (the Web-friendly preset)
    public static final JsonLdPreset DEFAULT_ACTIVE_PRESET = JsonLdPreset.WEB_FRIENDLY;
    public static final boolean DEFAULT_INCLUDE_ONTOLOGY_PREFIXES = true;
    public static final boolean DEFAULT_PRUNE_UNUSED_PREFIXES = true;
    public static final boolean DEFAULT_USE_VOCAB = false;
    public static final GenerateTermsFor DEFAULT_GENERATE_TERMS_FOR = GenerateTermsFor.ALL_PROPERTIES;
    public static final TermNameSource DEFAULT_TERM_NAME_SOURCE = TermNameSource.LOCAL_NAME;
    public static final TermCollisionPolicy DEFAULT_TERM_COLLISION_POLICY = TermCollisionPolicy.PREFIXED_IRI;
    public static final DocumentForm DEFAULT_DOCUMENT_FORM = DocumentForm.COMPACTED;
    public static final boolean DEFAULT_COMPACT_ARRAYS = true;
    public static final KeyOrdering DEFAULT_KEY_ORDERING = KeyOrdering.KEYWORDS_FIRST;
    public static final boolean DEFAULT_PRETTY_PRINT = true;
    public static final int DEFAULT_INDENT_SPACES = 2;
    public static final int MIN_INDENT_SPACES = 1;
    public static final int MAX_INDENT_SPACES = 8;
    public static final boolean DEFAULT_USE_NATIVE_TYPES = true;
    public static final TypeCoercionMode DEFAULT_TYPE_COERCION_MODE = TypeCoercionMode.ID;
    public static final boolean DEFAULT_USE_RDF_TYPE = false;
    public static final boolean DEFAULT_USE_LANGUAGE_MAPS = true;
    public static final BlankNodeEmbedding DEFAULT_BLANK_NODE_EMBEDDING = BlankNodeEmbedding.EMBED_ONCE;
    public static final boolean DEFAULT_INCLUDE_IMPORTS_CLOSURE = false;

    private static final JsonLdPreferences INSTANCE =
            new JsonLdPreferences(() -> PreferencesManager.getInstance().getApplicationPreferences(PREFERENCES_KEY));

    // Null for settings that are not connected to a store (see ofPreset)
    @Nullable
    private final Supplier<Preferences> store;

    private JsonLdPreset activePreset;

    // Context & Prefixes
    private boolean includeOntologyPrefixes;
    private boolean pruneUnusedPrefixes;
    private boolean useVocab;
    private GenerateTermsFor generateTermsFor;
    private TermNameSource termNameSource;
    private TermCollisionPolicy termCollisionPolicy;

    // Output Shape & Formatting
    private DocumentForm documentForm;
    private boolean compactArrays;
    private KeyOrdering keyOrdering;
    private boolean prettyPrint;
    private int indentSpaces;

    // Literals & Types
    private boolean useNativeTypes;
    private TypeCoercionMode typeCoercionMode;
    private boolean useRdfType;
    private boolean useLanguageMaps;

    // Structures & Scope
    private BlankNodeEmbedding blankNodeEmbedding;
    private boolean includeImportsClosure;

    /**
     * Creates settings backed by the given store and loads them from it.
     * Protégé itself uses {@link #getInstance()}; this constructor is for other stores, such as in tests.
     *
     * @param store supplies the preference store to read and write
     */
    public JsonLdPreferences(@Nonnull Supplier<Preferences> store) {
        this.store = Objects.requireNonNull(store);
        load();
    }

    private JsonLdPreferences(@Nonnull JsonLdPreset preset) {
        this.store = null;
        // Start from the defaults, so CUSTOM (which keeps the current values) has values too
        applyPreset(DEFAULT_ACTIVE_PRESET);
        applyPreset(preset);
    }

    /**
     * Returns the application-wide settings used by new JSON-LD storers.
     *
     * @return the shared, mutable settings instance
     */
    public static JsonLdPreferences getInstance() {
        return INSTANCE;
    }

    /**
     * The values of a preset, not connected to any store: they cannot be loaded or saved.
     * The preferences panel uses this to show a preset before the user clicks Apply or OK.
     *
     * @param preset the preset whose values should be shown
     * @return a detached settings instance initialized with that preset
     */
    @Nonnull
    public static JsonLdPreferences ofPreset(@Nonnull JsonLdPreset preset) {
        return new JsonLdPreferences(Objects.requireNonNull(preset));
    }

    /**
     * Loads the settings from the store. Missing or no longer valid values fall back to the defaults.
     */
    public synchronized void load() {
        Preferences prefs = requireStore().get();
        this.activePreset = loadEnum(prefs, KEY_ACTIVE_PRESET, JsonLdPreset.class, DEFAULT_ACTIVE_PRESET);
        this.includeOntologyPrefixes = prefs.getBoolean(KEY_INCLUDE_ONTOLOGY_PREFIXES, DEFAULT_INCLUDE_ONTOLOGY_PREFIXES);
        this.pruneUnusedPrefixes = prefs.getBoolean(KEY_PRUNE_UNUSED_PREFIXES, DEFAULT_PRUNE_UNUSED_PREFIXES);
        this.useVocab = prefs.getBoolean(KEY_USE_VOCAB, DEFAULT_USE_VOCAB);
        this.generateTermsFor = loadEnum(prefs, KEY_GENERATE_TERMS_FOR, GenerateTermsFor.class, DEFAULT_GENERATE_TERMS_FOR);
        this.termNameSource = loadEnum(prefs, KEY_TERM_NAME_SOURCE, TermNameSource.class, DEFAULT_TERM_NAME_SOURCE);
        this.termCollisionPolicy = loadEnum(prefs, KEY_TERM_COLLISION_POLICY, TermCollisionPolicy.class, DEFAULT_TERM_COLLISION_POLICY);
        this.documentForm = loadEnum(prefs, KEY_DOCUMENT_FORM, DocumentForm.class, DEFAULT_DOCUMENT_FORM);
        this.compactArrays = prefs.getBoolean(KEY_COMPACT_ARRAYS, DEFAULT_COMPACT_ARRAYS);
        this.keyOrdering = loadEnum(prefs, KEY_KEY_ORDERING, KeyOrdering.class, DEFAULT_KEY_ORDERING);
        this.prettyPrint = prefs.getBoolean(KEY_PRETTY_PRINT, DEFAULT_PRETTY_PRINT);
        this.indentSpaces = clampIndent(prefs.getInt(KEY_INDENT_SPACES, DEFAULT_INDENT_SPACES));
        this.useNativeTypes = prefs.getBoolean(KEY_USE_NATIVE_TYPES, DEFAULT_USE_NATIVE_TYPES);
        this.typeCoercionMode = loadEnum(prefs, KEY_TYPE_COERCION_MODE, TypeCoercionMode.class, DEFAULT_TYPE_COERCION_MODE);
        this.useRdfType = prefs.getBoolean(KEY_USE_RDF_TYPE, DEFAULT_USE_RDF_TYPE);
        this.useLanguageMaps = prefs.getBoolean(KEY_USE_LANGUAGE_MAPS, DEFAULT_USE_LANGUAGE_MAPS);
        this.blankNodeEmbedding = loadEnum(prefs, KEY_BLANK_NODE_EMBEDDING, BlankNodeEmbedding.class, DEFAULT_BLANK_NODE_EMBEDDING);
        this.includeImportsClosure = prefs.getBoolean(KEY_INCLUDE_IMPORTS_CLOSURE, DEFAULT_INCLUDE_IMPORTS_CLOSURE);
    }

    /**
     * Writes the current settings to the store.
     */
    public synchronized void save() {
        Preferences prefs = requireStore().get();
        prefs.putString(KEY_ACTIVE_PRESET, activePreset.name());
        prefs.putBoolean(KEY_INCLUDE_ONTOLOGY_PREFIXES, includeOntologyPrefixes);
        prefs.putBoolean(KEY_PRUNE_UNUSED_PREFIXES, pruneUnusedPrefixes);
        prefs.putBoolean(KEY_USE_VOCAB, useVocab);
        prefs.putString(KEY_GENERATE_TERMS_FOR, generateTermsFor.name());
        prefs.putString(KEY_TERM_NAME_SOURCE, termNameSource.name());
        prefs.putString(KEY_TERM_COLLISION_POLICY, termCollisionPolicy.name());
        prefs.putString(KEY_DOCUMENT_FORM, documentForm.name());
        prefs.putBoolean(KEY_COMPACT_ARRAYS, compactArrays);
        prefs.putString(KEY_KEY_ORDERING, keyOrdering.name());
        prefs.putBoolean(KEY_PRETTY_PRINT, prettyPrint);
        prefs.putInt(KEY_INDENT_SPACES, indentSpaces);
        prefs.putBoolean(KEY_USE_NATIVE_TYPES, useNativeTypes);
        prefs.putString(KEY_TYPE_COERCION_MODE, typeCoercionMode.name());
        prefs.putBoolean(KEY_USE_RDF_TYPE, useRdfType);
        prefs.putBoolean(KEY_USE_LANGUAGE_MAPS, useLanguageMaps);
        prefs.putString(KEY_BLANK_NODE_EMBEDDING, blankNodeEmbedding.name());
        prefs.putBoolean(KEY_INCLUDE_IMPORTS_CLOSURE, includeImportsClosure);
    }

    /**
     * Resets all settings to the defaults (the Web-friendly preset). Does not save.
     */
    public synchronized void resetToDefaults() {
        applyPreset(DEFAULT_ACTIVE_PRESET);
    }

    /**
     * Sets all settings to the values of the given preset. {@link JsonLdPreset#CUSTOM} keeps the current values.
     * Does not save.
     *
     * @param preset the preset to apply
     */
    public synchronized void applyPreset(@Nonnull JsonLdPreset preset) {
        Objects.requireNonNull(preset, "preset must not be null");
        this.activePreset = preset;
        if (preset == JsonLdPreset.CUSTOM) {
            return;
        }
        applyWebFriendlyValues();
        switch (preset) {
            case ROUND_TRIP_SAFE:
                this.pruneUnusedPrefixes = false;
                this.generateTermsFor = GenerateTermsFor.NONE;
                this.compactArrays = false;
                this.keyOrdering = KeyOrdering.NONE;
                this.useNativeTypes = false;
                this.typeCoercionMode = TypeCoercionMode.NONE;
                this.useRdfType = true;
                this.useLanguageMaps = false;
                this.blankNodeEmbedding = BlankNodeEmbedding.REFERENCE;
                break;
            case DIFF_FRIENDLY:
                this.generateTermsFor = GenerateTermsFor.NONE;
                this.pruneUnusedPrefixes = false;
                this.compactArrays = false;
                break;
            case EXPANDED:
                this.documentForm = DocumentForm.EXPANDED;
                this.compactArrays = false;
                this.generateTermsFor = GenerateTermsFor.NONE;
                this.keyOrdering = KeyOrdering.NONE;
                this.useNativeTypes = false;
                this.useRdfType = true;
                this.useLanguageMaps = false;
                this.blankNodeEmbedding = BlankNodeEmbedding.REFERENCE;
                break;
            default:
                break;
        }
    }

    private void applyWebFriendlyValues() {
        this.includeOntologyPrefixes = DEFAULT_INCLUDE_ONTOLOGY_PREFIXES;
        this.pruneUnusedPrefixes = DEFAULT_PRUNE_UNUSED_PREFIXES;
        this.useVocab = DEFAULT_USE_VOCAB;
        this.generateTermsFor = DEFAULT_GENERATE_TERMS_FOR;
        this.termNameSource = DEFAULT_TERM_NAME_SOURCE;
        this.termCollisionPolicy = DEFAULT_TERM_COLLISION_POLICY;
        this.documentForm = DEFAULT_DOCUMENT_FORM;
        this.compactArrays = DEFAULT_COMPACT_ARRAYS;
        this.keyOrdering = DEFAULT_KEY_ORDERING;
        this.prettyPrint = DEFAULT_PRETTY_PRINT;
        this.indentSpaces = DEFAULT_INDENT_SPACES;
        this.useNativeTypes = DEFAULT_USE_NATIVE_TYPES;
        this.typeCoercionMode = DEFAULT_TYPE_COERCION_MODE;
        this.useRdfType = DEFAULT_USE_RDF_TYPE;
        this.useLanguageMaps = DEFAULT_USE_LANGUAGE_MAPS;
        this.blankNodeEmbedding = DEFAULT_BLANK_NODE_EMBEDDING;
        this.includeImportsClosure = DEFAULT_INCLUDE_IMPORTS_CLOSURE;
    }

    private Supplier<Preferences> requireStore() {
        if (store == null) {
            throw new IllegalStateException("These JSON-LD settings are not connected to a store");
        }
        return store;
    }

    private static int clampIndent(int indent) {
        return Math.max(MIN_INDENT_SPACES, Math.min(MAX_INDENT_SPACES, indent));
    }

    private static <E extends Enum<E>> E loadEnum(Preferences prefs, String key, Class<E> enumClass, E defaultValue) {
        String name = prefs.getString(key, null);
        if (name != null) {
            try {
                return Enum.valueOf(enumClass, name);
            } catch (IllegalArgumentException e) {
                LOGGER.warn("Unknown value '{}' for JSON-LD setting '{}', using the default '{}'", name, key, defaultValue);
            }
        }
        return defaultValue;
    }

    // --- Getters & Setters ---

    public JsonLdPreset getActivePreset() { return activePreset; }
    /** Records the selected preset without applying its values; use {@link #applyPreset} to do that. */
    public void setActivePreset(JsonLdPreset activePreset) { this.activePreset = activePreset; }

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


    public boolean isCompactArrays() { return compactArrays; }
    public void setCompactArrays(boolean compactArrays) { this.compactArrays = compactArrays; }

    public KeyOrdering getKeyOrdering() { return keyOrdering; }
    public void setKeyOrdering(KeyOrdering keyOrdering) { this.keyOrdering = keyOrdering; }

    public boolean isPrettyPrint() { return prettyPrint; }
    public void setPrettyPrint(boolean prettyPrint) { this.prettyPrint = prettyPrint; }

    public int getIndentSpaces() { return indentSpaces; }
    /** Sets indentation, clamped to the supported range of one to eight spaces. */
    public void setIndentSpaces(int indentSpaces) { this.indentSpaces = clampIndent(indentSpaces); }

    public boolean isUseNativeTypes() { return useNativeTypes; }
    public void setUseNativeTypes(boolean useNativeTypes) { this.useNativeTypes = useNativeTypes; }

    public TypeCoercionMode getTypeCoercionMode() { return typeCoercionMode; }
    public void setTypeCoercionMode(TypeCoercionMode typeCoercionMode) { this.typeCoercionMode = typeCoercionMode; }

    public boolean isUseRdfType() { return useRdfType; }
    public void setUseRdfType(boolean useRdfType) { this.useRdfType = useRdfType; }

    public boolean isUseLanguageMaps() { return useLanguageMaps; }
    public void setUseLanguageMaps(boolean useLanguageMaps) { this.useLanguageMaps = useLanguageMaps; }

    public BlankNodeEmbedding getBlankNodeEmbedding() { return blankNodeEmbedding; }
    public void setBlankNodeEmbedding(BlankNodeEmbedding blankNodeEmbedding) { this.blankNodeEmbedding = blankNodeEmbedding; }

    public boolean isIncludeImportsClosure() { return includeImportsClosure; }
    public void setIncludeImportsClosure(boolean includeImportsClosure) { this.includeImportsClosure = includeImportsClosure; }
}
