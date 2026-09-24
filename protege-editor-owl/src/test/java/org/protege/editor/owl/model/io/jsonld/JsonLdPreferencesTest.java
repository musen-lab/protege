package org.protege.editor.owl.model.io.jsonld;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class JsonLdPreferencesTest {

    private JsonLdPreferences prefs;

    @Before
    public void setUp() {
        prefs = JsonLdPreferences.getInstance();
        prefs.resetToDefaults();
    }

    @Test
    public void testDefaultValues() {
        assertEquals(JsonLdPreset.WEB_FRIENDLY, prefs.getActivePreset());
        assertEquals(ContextSource.GENERATED, prefs.getContextSource());
        assertEquals(ContextEmbedding.INLINE, prefs.getContextEmbedding());
        assertTrue(prefs.isIncludeOntologyPrefixes());
        assertTrue(prefs.isPruneUnusedPrefixes());
        assertFalse(prefs.isUseVocab());
        assertEquals(GenerateTermsFor.ALL_PROPERTIES, prefs.getGenerateTermsFor());
        assertEquals(TermNameSource.LOCAL_NAME, prefs.getTermNameSource());
        assertEquals(TermCollisionPolicy.PREFIXED_IRI, prefs.getTermCollisionPolicy());
        assertEquals(DocumentForm.COMPACTED, prefs.getDocumentForm());
        assertTrue(prefs.isWrapInGraphArray());
        assertTrue(prefs.isCompactArrays());
        assertEquals(KeyOrdering.KEYWORDS_FIRST, prefs.getKeyOrdering());
        assertTrue(prefs.isPrettyPrint());
        assertEquals(2, prefs.getIndentSpaces());
        assertTrue(prefs.isUseNativeTypes());
        assertTrue(prefs.isOmitXsdStringTypes());
        assertEquals(TypeCoercionMode.AUTO, prefs.getTypeCoercionMode());
        assertFalse(prefs.isUseRdfType());
        assertTrue(prefs.isNullLanguageForUntagged());
        assertTrue(prefs.isUseLanguageMaps());
        assertEquals(BlankNodeLabeling.DETERMINISTIC, prefs.getBlankNodeLabeling());
        assertEquals(BlankNodeEmbedding.EMBED_ONCE, prefs.getBlankNodeEmbedding());
        assertEquals(RdfListHandling.LIST_CONTAINER, prefs.getRdfListHandling());
        assertEquals("owl:unionOf,owl:intersectionOf,owl:oneOf", prefs.getListPropertyOverrides());
        assertFalse(prefs.isIncludeImportsClosure());
    }

    @Test
    public void testApplyRoundTripSafePreset() {
        prefs.applyPreset(JsonLdPreset.ROUND_TRIP_SAFE);

        assertEquals(JsonLdPreset.ROUND_TRIP_SAFE, prefs.getActivePreset());
        assertEquals(DocumentForm.COMPACTED, prefs.getDocumentForm());
        assertFalse(prefs.isUseNativeTypes());
        assertFalse(prefs.isOmitXsdStringTypes());
        assertFalse(prefs.isUseLanguageMaps());
        assertEquals(BlankNodeEmbedding.REFERENCE, prefs.getBlankNodeEmbedding());
        assertEquals(RdfListHandling.NATIVE_LIST, prefs.getRdfListHandling());
        assertEquals(BlankNodeLabeling.PRESERVE, prefs.getBlankNodeLabeling());
        assertTrue(prefs.isUseRdfType());
        assertEquals(GenerateTermsFor.NONE, prefs.getGenerateTermsFor());
    }

    @Test
    public void testApplyDiffFriendlyPreset() {
        prefs.applyPreset(JsonLdPreset.DIFF_FRIENDLY);

        assertEquals(JsonLdPreset.DIFF_FRIENDLY, prefs.getActivePreset());
        assertEquals(KeyOrdering.KEYWORDS_FIRST, prefs.getKeyOrdering());
        assertEquals(BlankNodeLabeling.DETERMINISTIC, prefs.getBlankNodeLabeling());
        assertTrue(prefs.isPrettyPrint());
        assertEquals(2, prefs.getIndentSpaces());
        assertTrue(prefs.isPruneUnusedPrefixes());
    }

    @Test
    public void testApplyExpandedPreset() {
        prefs.applyPreset(JsonLdPreset.EXPANDED);

        assertEquals(JsonLdPreset.EXPANDED, prefs.getActivePreset());
        assertEquals(DocumentForm.EXPANDED, prefs.getDocumentForm());
        assertFalse(prefs.isCompactArrays());
        assertEquals(ContextEmbedding.NONE, prefs.getContextEmbedding());
        assertFalse(prefs.isIncludeOntologyPrefixes());
        assertTrue(prefs.isUseRdfType());
    }

    @Test
    public void testResetToDefaults() {
        prefs.applyPreset(JsonLdPreset.EXPANDED);
        assertEquals(DocumentForm.EXPANDED, prefs.getDocumentForm());

        prefs.resetToDefaults();
        assertEquals(JsonLdPreset.WEB_FRIENDLY, prefs.getActivePreset());
        assertEquals(DocumentForm.COMPACTED, prefs.getDocumentForm());
        assertTrue(prefs.isUseNativeTypes());
    }

    @Test
    public void testIndividualSetters() {
        prefs.setIndentSpaces(4);
        assertEquals(4, prefs.getIndentSpaces());

        prefs.setUseNativeTypes(false);
        assertFalse(prefs.isUseNativeTypes());

        prefs.setDocumentForm(DocumentForm.FLATTENED);
        assertEquals(DocumentForm.FLATTENED, prefs.getDocumentForm());
    }
}
