package org.protege.editor.owl.ui.preferences;

import org.junit.Before;
import org.junit.Test;
import org.protege.editor.owl.model.io.jsonld.*;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class JsonLdPreferencesPanelTest {

    private static final List<String> SETTING_KEYS = Arrays.asList(
            JsonLdPreferences.KEY_INCLUDE_ONTOLOGY_PREFIXES, JsonLdPreferences.KEY_PRUNE_UNUSED_PREFIXES,
            JsonLdPreferences.KEY_USE_VOCAB, JsonLdPreferences.KEY_GENERATE_TERMS_FOR,
            JsonLdPreferences.KEY_TERM_NAME_SOURCE, JsonLdPreferences.KEY_TERM_COLLISION_POLICY,
            JsonLdPreferences.KEY_DOCUMENT_FORM, JsonLdPreferences.KEY_COMPACT_ARRAYS,
            JsonLdPreferences.KEY_KEY_ORDERING, JsonLdPreferences.KEY_PRETTY_PRINT,
            JsonLdPreferences.KEY_INDENT_SPACES, JsonLdPreferences.KEY_USE_NATIVE_TYPES,
            JsonLdPreferences.KEY_TYPE_COERCION_MODE, JsonLdPreferences.KEY_USE_RDF_TYPE,
            JsonLdPreferences.KEY_USE_LANGUAGE_MAPS, JsonLdPreferences.KEY_BLANK_NODE_EMBEDDING,
            JsonLdPreferences.KEY_INCLUDE_IMPORTS_CLOSURE);

    private InMemoryPreferences store;

    private JsonLdPreferences prefs;

    private JsonLdPreferencesPanel panel;

    @Before
    public void setUp() throws Exception {
        System.setProperty("java.awt.headless", "true");
        store = new InMemoryPreferences();
        prefs = new JsonLdPreferences(() -> store);
        panel = new JsonLdPreferencesPanel(prefs);
        panel.initialise();
    }

    @SuppressWarnings("unchecked")
    private <T extends Component> T control(String name) {
        Component found = find(panel, name);
        assertNotNull("No control named " + name, found);
        return (T) found;
    }

    private static Component find(Container container, String name) {
        for (Component c : container.getComponents()) {
            if (name.equals(c.getName())) {
                return c;
            }
            if (c instanceof Container) {
                Component found = find((Container) c, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private JComboBox<Object> combo(String name) {
        return control(name);
    }

    private JCheckBox checkBox(String name) {
        return control(name);
    }

    private JsonLdPreferences reloadStore() {
        return new JsonLdPreferences(() -> store);
    }

    @Test
    public void showsAllSeventeenSettings() {
        for (String key : SETTING_KEYS) {
            assertNotNull(control(key));
        }
    }

    @Test
    public void showsTheCurrentSettings() throws Exception {
        prefs.setDocumentForm(DocumentForm.FLATTENED);
        prefs.setIndentSpaces(5);
        panel.initialise();
        assertEquals(DocumentForm.FLATTENED, combo(JsonLdPreferences.KEY_DOCUMENT_FORM).getSelectedItem());
        assertEquals(5, ((JSpinner) control(JsonLdPreferences.KEY_INDENT_SPACES)).getValue());
    }

    @Test
    public void choosingAPresetOnlyChangesTheScreen() {
        combo(JsonLdPreferences.KEY_ACTIVE_PRESET).setSelectedItem(JsonLdPreset.EXPANDED);

        assertEquals(DocumentForm.EXPANDED, combo(JsonLdPreferences.KEY_DOCUMENT_FORM).getSelectedItem());
        assertEquals(JsonLdPreset.EXPANDED, combo(JsonLdPreferences.KEY_ACTIVE_PRESET).getSelectedItem());
        // Cancel = applyChanges() is never called: the settings in use and in the store are unchanged
        assertEquals(DocumentForm.COMPACTED, prefs.getDocumentForm());
        assertEquals(JsonLdPreset.WEB_FRIENDLY, prefs.getActivePreset());
        assertEquals(DocumentForm.COMPACTED, reloadStore().getDocumentForm());
    }

    @Test
    public void applySavesWhatIsOnScreen() {
        combo(JsonLdPreferences.KEY_ACTIVE_PRESET).setSelectedItem(JsonLdPreset.ROUND_TRIP_SAFE);
        panel.applyChanges();

        assertEquals(JsonLdPreset.ROUND_TRIP_SAFE, prefs.getActivePreset());
        assertFalse(prefs.isUseNativeTypes());
        JsonLdPreferences reloaded = reloadStore();
        assertEquals(JsonLdPreset.ROUND_TRIP_SAFE, reloaded.getActivePreset());
        assertFalse(reloaded.isUseNativeTypes());
        assertEquals(BlankNodeEmbedding.REFERENCE, reloaded.getBlankNodeEmbedding());
    }

    @Test
    public void resetOnlyChangesTheScreenUntilApply() throws Exception {
        prefs.applyPreset(JsonLdPreset.EXPANDED);
        panel.initialise();

        ((JButton) control("reset")).doClick(0);
        assertEquals(DocumentForm.COMPACTED, combo(JsonLdPreferences.KEY_DOCUMENT_FORM).getSelectedItem());
        assertEquals(JsonLdPreset.WEB_FRIENDLY, combo(JsonLdPreferences.KEY_ACTIVE_PRESET).getSelectedItem());
        assertEquals("Not applied yet", DocumentForm.EXPANDED, prefs.getDocumentForm());

        panel.applyChanges();
        assertEquals(DocumentForm.COMPACTED, prefs.getDocumentForm());
        assertEquals(DocumentForm.COMPACTED, reloadStore().getDocumentForm());
    }

    @Test
    public void changingOneSettingSwitchesThePresetToCustom() {
        checkBox(JsonLdPreferences.KEY_USE_VOCAB).doClick(0);
        assertEquals(JsonLdPreset.CUSTOM, combo(JsonLdPreferences.KEY_ACTIVE_PRESET).getSelectedItem());

        panel.applyChanges();
        assertEquals(JsonLdPreset.CUSTOM, reloadStore().getActivePreset());
        assertTrue(reloadStore().isUseVocab());
    }

    @Test
    public void expandedGreysOutEverythingThatNeedsAContext() {
        combo(JsonLdPreferences.KEY_DOCUMENT_FORM).setSelectedItem(DocumentForm.EXPANDED);
        for (String key : Arrays.asList(
                JsonLdPreferences.KEY_INCLUDE_ONTOLOGY_PREFIXES, JsonLdPreferences.KEY_PRUNE_UNUSED_PREFIXES,
                JsonLdPreferences.KEY_USE_VOCAB, JsonLdPreferences.KEY_GENERATE_TERMS_FOR,
                JsonLdPreferences.KEY_TERM_NAME_SOURCE, JsonLdPreferences.KEY_TERM_COLLISION_POLICY,
                JsonLdPreferences.KEY_COMPACT_ARRAYS, JsonLdPreferences.KEY_TYPE_COERCION_MODE,
                JsonLdPreferences.KEY_USE_LANGUAGE_MAPS)) {
            assertFalse(key + " should be greyed out", control(key).isEnabled());
        }
        for (String key : Arrays.asList(
                JsonLdPreferences.KEY_KEY_ORDERING, JsonLdPreferences.KEY_PRETTY_PRINT,
                JsonLdPreferences.KEY_USE_NATIVE_TYPES, JsonLdPreferences.KEY_USE_RDF_TYPE,
                JsonLdPreferences.KEY_BLANK_NODE_EMBEDDING, JsonLdPreferences.KEY_INCLUDE_IMPORTS_CLOSURE)) {
            assertTrue(key + " should stay available", control(key).isEnabled());
        }

        combo(JsonLdPreferences.KEY_DOCUMENT_FORM).setSelectedItem(DocumentForm.COMPACTED);
        assertTrue(control(JsonLdPreferences.KEY_USE_LANGUAGE_MAPS).isEnabled());
    }

    @Test
    public void flattenedGreysOutNesting() {
        combo(JsonLdPreferences.KEY_DOCUMENT_FORM).setSelectedItem(DocumentForm.FLATTENED);
        assertFalse(control(JsonLdPreferences.KEY_BLANK_NODE_EMBEDDING).isEnabled());
        assertTrue(control(JsonLdPreferences.KEY_GENERATE_TERMS_FOR).isEnabled());
    }

    @Test
    public void noShortNamesGreysOutNameSettings() {
        combo(JsonLdPreferences.KEY_GENERATE_TERMS_FOR).setSelectedItem(GenerateTermsFor.NONE);
        assertFalse(control(JsonLdPreferences.KEY_TERM_NAME_SOURCE).isEnabled());
        assertFalse(control(JsonLdPreferences.KEY_TERM_COLLISION_POLICY).isEnabled());
    }

    @Test
    public void prettyPrintOffGreysOutIndent() {
        checkBox(JsonLdPreferences.KEY_PRETTY_PRINT).doClick(0);
        assertFalse(control(JsonLdPreferences.KEY_INDENT_SPACES).isEnabled());
    }

    @Test
    public void rdfTypeLabelSaysWhatTheBoxDoes() {
        JCheckBox box = checkBox(JsonLdPreferences.KEY_USE_RDF_TYPE);
        assertEquals("Write rdf:type as a normal property (instead of @type)", box.getText());
        box.doClick(0);
        panel.applyChanges();
        assertTrue("Ticked means rdf:type as a property", reloadStore().isUseRdfType());
    }

    @Test
    public void initialisingTwiceDoesNotDuplicateTheContent() throws Exception {
        int count = panel.getComponentCount();
        panel.initialise();
        assertEquals(count, panel.getComponentCount());
    }
}
