package org.protege.editor.owl.model.io.jsonld;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class JsonLdPreferencesTest {

    private InMemoryPreferences store;

    private JsonLdPreferences prefs;

    @Before
    public void setUp() {
        store = new InMemoryPreferences();
        prefs = new JsonLdPreferences(() -> store);
    }

    @Test
    public void emptyStoreGivesWebFriendlyDefaults() {
        assertEquals(JsonLdPreset.WEB_FRIENDLY, prefs.getActivePreset());
        assertWebFriendly(prefs);
    }

    @Test
    public void resetToDefaultsRestoresWebFriendly() {
        prefs.applyPreset(JsonLdPreset.EXPANDED);
        prefs.resetToDefaults();
        assertEquals(JsonLdPreset.WEB_FRIENDLY, prefs.getActivePreset());
        assertWebFriendly(prefs);
    }

    @Test
    public void roundTripSafePreset() {
        prefs.applyPreset(JsonLdPreset.ROUND_TRIP_SAFE);

        assertEquals(JsonLdPreset.ROUND_TRIP_SAFE, prefs.getActivePreset());
        assertFalse(prefs.isPruneUnusedPrefixes());
        assertEquals(GenerateTermsFor.NONE, prefs.getGenerateTermsFor());
        assertEquals(DocumentForm.COMPACTED, prefs.getDocumentForm());
        assertFalse(prefs.isCompactArrays());
        assertEquals(KeyOrdering.NONE, prefs.getKeyOrdering());
        assertFalse(prefs.isUseNativeTypes());
        assertEquals(TypeCoercionMode.NONE, prefs.getTypeCoercionMode());
        assertTrue(prefs.isUseRdfType());
        assertFalse(prefs.isUseLanguageMaps());
        assertEquals(BlankNodeEmbedding.REFERENCE, prefs.getBlankNodeEmbedding());
    }

    @Test
    public void diffFriendlyPresetDiffersFromWebFriendlyOnlyWhereIntended() {
        prefs.applyPreset(JsonLdPreset.DIFF_FRIENDLY);

        assertEquals(JsonLdPreset.DIFF_FRIENDLY, prefs.getActivePreset());
        assertEquals(GenerateTermsFor.NONE, prefs.getGenerateTermsFor());
        assertFalse(prefs.isPruneUnusedPrefixes());
        assertFalse(prefs.isCompactArrays());
        // Everything else as in Web-friendly
        assertEquals(DocumentForm.COMPACTED, prefs.getDocumentForm());
        assertEquals(KeyOrdering.KEYWORDS_FIRST, prefs.getKeyOrdering());
        assertTrue(prefs.isUseNativeTypes());
        assertEquals(TypeCoercionMode.ID, prefs.getTypeCoercionMode());
        assertTrue(prefs.isUseLanguageMaps());
        assertEquals(BlankNodeEmbedding.EMBED_ONCE, prefs.getBlankNodeEmbedding());
    }

    @Test
    public void expandedPreset() {
        prefs.applyPreset(JsonLdPreset.EXPANDED);

        assertEquals(JsonLdPreset.EXPANDED, prefs.getActivePreset());
        assertEquals(DocumentForm.EXPANDED, prefs.getDocumentForm());
        assertFalse(prefs.isCompactArrays());
        assertEquals(GenerateTermsFor.NONE, prefs.getGenerateTermsFor());
        assertEquals(KeyOrdering.NONE, prefs.getKeyOrdering());
        assertFalse(prefs.isUseNativeTypes());
        assertTrue(prefs.isUseRdfType());
        assertFalse(prefs.isUseLanguageMaps());
        assertEquals(BlankNodeEmbedding.REFERENCE, prefs.getBlankNodeEmbedding());
    }

    @Test
    public void customPresetKeepsCurrentValues() {
        prefs.setIndentSpaces(5);
        prefs.setUseVocab(true);
        prefs.applyPreset(JsonLdPreset.CUSTOM);

        assertEquals(JsonLdPreset.CUSTOM, prefs.getActivePreset());
        assertEquals(5, prefs.getIndentSpaces());
        assertTrue(prefs.isUseVocab());
    }

    @Test
    public void savedValuesSurviveARestart() {
        prefs.applyPreset(JsonLdPreset.DIFF_FRIENDLY);
        prefs.setActivePreset(JsonLdPreset.CUSTOM);
        prefs.setUseVocab(true);
        prefs.setTermNameSource(TermNameSource.RDFS_LABEL);
        prefs.setTermCollisionPolicy(TermCollisionPolicy.DISAMBIGUATE);
        prefs.setDocumentForm(DocumentForm.FLATTENED);
        prefs.setIndentSpaces(4);
        prefs.setIncludeImportsClosure(true);
        prefs.save();

        JsonLdPreferences reloaded = new JsonLdPreferences(() -> store);

        assertEquals(JsonLdPreset.CUSTOM, reloaded.getActivePreset());
        assertTrue(reloaded.isIncludeOntologyPrefixes());
        assertFalse(reloaded.isPruneUnusedPrefixes());
        assertTrue(reloaded.isUseVocab());
        assertEquals(GenerateTermsFor.NONE, reloaded.getGenerateTermsFor());
        assertEquals(TermNameSource.RDFS_LABEL, reloaded.getTermNameSource());
        assertEquals(TermCollisionPolicy.DISAMBIGUATE, reloaded.getTermCollisionPolicy());
        assertEquals(DocumentForm.FLATTENED, reloaded.getDocumentForm());
        assertFalse(reloaded.isCompactArrays());
        assertEquals(KeyOrdering.KEYWORDS_FIRST, reloaded.getKeyOrdering());
        assertTrue(reloaded.isPrettyPrint());
        assertEquals(4, reloaded.getIndentSpaces());
        assertTrue(reloaded.isUseNativeTypes());
        assertEquals(TypeCoercionMode.ID, reloaded.getTypeCoercionMode());
        assertFalse(reloaded.isUseRdfType());
        assertTrue(reloaded.isUseLanguageMaps());
        assertEquals(BlankNodeEmbedding.EMBED_ONCE, reloaded.getBlankNodeEmbedding());
        assertTrue(reloaded.isIncludeImportsClosure());
    }

    @Test
    public void choicesThatNoLongerExistFallBackToDefaults() {
        // Values the first implementation could have stored
        store.putString(JsonLdPreferences.KEY_TYPE_COERCION_MODE, "AUTO");
        store.putString(JsonLdPreferences.KEY_BLANK_NODE_EMBEDDING, "EMBED_ALWAYS");
        store.putString(JsonLdPreferences.KEY_TERM_NAME_SOURCE, "SKOS_PREFLABEL");

        JsonLdPreferences loaded = new JsonLdPreferences(() -> store);

        assertEquals(TypeCoercionMode.ID, loaded.getTypeCoercionMode());
        assertEquals(BlankNodeEmbedding.EMBED_ONCE, loaded.getBlankNodeEmbedding());
        assertEquals(TermNameSource.LOCAL_NAME, loaded.getTermNameSource());
    }

    @Test
    public void storedValuesForRemovedSettingsAreIgnored() {
        store.putString("contextEmbedding", "NONE");
        store.putString("blankNodeLabeling", "SKOLEMIZE");
        store.putBoolean("omitXsdStringTypes", false);

        JsonLdPreferences loaded = new JsonLdPreferences(() -> store);

        assertWebFriendly(loaded);
    }

    @Test
    public void presetValuesWithoutAStore() {
        JsonLdPreferences expanded = JsonLdPreferences.ofPreset(JsonLdPreset.EXPANDED);
        assertEquals(DocumentForm.EXPANDED, expanded.getDocumentForm());
        assertEquals(JsonLdPreset.EXPANDED, expanded.getActivePreset());

        JsonLdPreferences custom = JsonLdPreferences.ofPreset(JsonLdPreset.CUSTOM);
        assertEquals(JsonLdPreset.CUSTOM, custom.getActivePreset());
        assertWebFriendly(custom);

        try {
            expanded.save();
            fail("Preset values are not connected to a store");
        } catch (IllegalStateException expected) {
            // expected
        }
    }

    @Test
    public void indentIsKeptBetweenOneAndEight() {
        prefs.setIndentSpaces(0);
        assertEquals(1, prefs.getIndentSpaces());
        prefs.setIndentSpaces(12);
        assertEquals(8, prefs.getIndentSpaces());

        store.putInt(JsonLdPreferences.KEY_INDENT_SPACES, 0);
        assertEquals(1, new JsonLdPreferences(() -> store).getIndentSpaces());
    }

    private static void assertWebFriendly(JsonLdPreferences p) {
        assertTrue(p.isIncludeOntologyPrefixes());
        assertTrue(p.isPruneUnusedPrefixes());
        assertFalse(p.isUseVocab());
        assertEquals(GenerateTermsFor.ALL_PROPERTIES, p.getGenerateTermsFor());
        assertEquals(TermNameSource.LOCAL_NAME, p.getTermNameSource());
        assertEquals(TermCollisionPolicy.PREFIXED_IRI, p.getTermCollisionPolicy());
        assertEquals(DocumentForm.COMPACTED, p.getDocumentForm());
        assertTrue(p.isCompactArrays());
        assertEquals(KeyOrdering.KEYWORDS_FIRST, p.getKeyOrdering());
        assertTrue(p.isPrettyPrint());
        assertEquals(2, p.getIndentSpaces());
        assertTrue(p.isUseNativeTypes());
        assertEquals(TypeCoercionMode.ID, p.getTypeCoercionMode());
        assertFalse(p.isUseRdfType());
        assertTrue(p.isUseLanguageMaps());
        assertEquals(BlankNodeEmbedding.EMBED_ONCE, p.getBlankNodeEmbedding());
        assertFalse(p.isIncludeImportsClosure());
    }
}
