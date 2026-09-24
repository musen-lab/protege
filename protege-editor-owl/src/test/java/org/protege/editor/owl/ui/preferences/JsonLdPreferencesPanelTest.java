package org.protege.editor.owl.ui.preferences;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.protege.editor.owl.model.io.jsonld.DocumentForm;
import org.protege.editor.owl.model.io.jsonld.JsonLdPreferences;
import org.protege.editor.owl.model.io.jsonld.JsonLdPreset;

import javax.swing.*;
import java.awt.*;

import static org.junit.Assert.*;

public class JsonLdPreferencesPanelTest {

    @Before
    public void setUp() {
        // Run headlessly
        System.setProperty("java.awt.headless", "true");
        JsonLdPreferences.getInstance().resetToDefaults();
    }

    @After
    public void tearDown() {
        JsonLdPreferences.getInstance().resetToDefaults();
    }

    @Test
    public void testPanelLifecycle() throws Exception {
        JsonLdPreferencesPanel panel = new JsonLdPreferencesPanel();
        panel.initialise();

        assertNotNull(panel);
        assertTrue(panel.getComponentCount() > 0);

        // Verify panel can apply changes cleanly
        panel.applyChanges();

        panel.dispose();
    }

    @Test
    public void testApplyChangesSavesModifiedValues() throws Exception {
        JsonLdPreferencesPanel panel = new JsonLdPreferencesPanel();
        panel.initialise();

        // Change a preference via JsonLdPreferences then re-init or apply
        JsonLdPreferences prefs = JsonLdPreferences.getInstance();
        prefs.applyPreset(JsonLdPreset.EXPANDED);

        panel.initialise();
        panel.applyChanges();

        assertEquals(DocumentForm.EXPANDED, prefs.getDocumentForm());
        assertEquals(JsonLdPreset.EXPANDED, prefs.getActivePreset());
    }
}
