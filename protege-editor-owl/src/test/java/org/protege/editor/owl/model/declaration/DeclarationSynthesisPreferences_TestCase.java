package org.protege.editor.owl.model.declaration;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.protege.editor.core.ProtegeApplication;
import org.protege.editor.core.prefs.PreferencesManager;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The preference is backed by real user preferences, so this test records what it found and puts it
 * back.  Running the suite must not change the developer's settings.
 */
public class DeclarationSynthesisPreferences_TestCase {

    private static final String PREFERENCES_KEY = "org.protege.editor.owl.declaration";

    private static final String KEEP_KEY = "declaration.keep.in.defining.ontology";

    private DeclarationSynthesisPreferences preferences;

    private boolean hadStoredValue;

    private boolean storedValue;

    @Before
    public void setUp() {
        preferences = DeclarationSynthesisPreferences.getPreferences();
        // Reading with both defaults tells a stored value apart from an absent one.
        boolean withTrueDefault = raw().getBoolean(KEEP_KEY, true);
        boolean withFalseDefault = raw().getBoolean(KEEP_KEY, false);
        hadStoredValue = withTrueDefault == withFalseDefault;
        storedValue = withTrueDefault;
        raw().clear();
    }

    @After
    public void tearDown() {
        raw().clear();
        if (hadStoredValue) {
            raw().putBoolean(KEEP_KEY, storedValue);
        }
    }

    /** Off by default: an untouched installation writes what Protege has always written. */
    @Test
    public void shouldDefaultToOffWhenNothingIsStored() {
        assertFalse(preferences.isKeepDeclarationsInDefiningOntology());
    }

    @Test
    public void shouldReadBackOn() {
        preferences.setKeepDeclarationsInDefiningOntology(true);
        assertTrue(preferences.isKeepDeclarationsInDefiningOntology());
    }

    @Test
    public void shouldReadBackOff() {
        preferences.setKeepDeclarationsInDefiningOntology(true);
        preferences.setKeepDeclarationsInDefiningOntology(false);
        assertFalse(preferences.isKeepDeclarationsInDefiningOntology());
    }

    /**
     * The accessor must read through, so a change made anywhere is seen by the next save without a
     * restart.
     */
    @Test
    public void shouldNotCacheTheStoredValue() {
        assertFalse(preferences.isKeepDeclarationsInDefiningOntology());
        raw().putBoolean(KEEP_KEY, true);
        assertTrue("the value was cached; a changed setting would need a restart",
                preferences.isKeepDeclarationsInDefiningOntology());
    }

    @Test
    public void shouldUseADedicatedPreferencesNode() {
        // Sharing a node with the application preferences would let an unrelated clear() wipe this.
        preferences.setKeepDeclarationsInDefiningOntology(true);
        assertFalse(PreferencesManager.getInstance()
                .getApplicationPreferences(ProtegeApplication.ID)
                .getBoolean(KEEP_KEY, false));
    }

    private org.protege.editor.core.prefs.Preferences raw() {
        return PreferencesManager.getInstance().getApplicationPreferences(PREFERENCES_KEY);
    }
}
