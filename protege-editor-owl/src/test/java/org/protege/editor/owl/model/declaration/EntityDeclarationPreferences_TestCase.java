package org.protege.editor.owl.model.declaration;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.protege.editor.core.ProtegeApplication;
import org.protege.editor.core.prefs.PreferencesManager;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The stored preference, read and written through {@code EntityDeclarationPreferences}.
 *
 * <p>It is backed by real user preferences, so this test records what it found and puts it back.
 * Running the suite must not change the developer's settings.
 */
public class EntityDeclarationPreferences_TestCase {

    private static final String PREFERENCES_KEY = "org.protege.editor.owl.declaration";

    private static final String SUPPRESS_KEY = "suppress.automatic.entity.declarations";

    private EntityDeclarationPreferences preferences;

    private boolean hadStoredValue;

    private boolean storedValue;

    @Before
    public void setUp() {
        preferences = EntityDeclarationPreferences.getInstance();
        // Reading with both defaults tells a stored value apart from an absent one.
        boolean withTrueDefault = raw().getBoolean(SUPPRESS_KEY, true);
        boolean withFalseDefault = raw().getBoolean(SUPPRESS_KEY, false);
        hadStoredValue = withTrueDefault == withFalseDefault;
        storedValue = withTrueDefault;
        raw().clear();
    }

    @After
    public void tearDown() {
        raw().clear();
        if (hadStoredValue) {
            raw().putBoolean(SUPPRESS_KEY, storedValue);
        }
    }

    /** Off by default: an untouched installation adds the declarations Protege has always added. */
    @Test
    public void shouldNotSuppressWhenNothingIsStored() {
        assertFalse(preferences.isSuppressingAutomaticDeclarations());
    }

    @Test
    public void shouldReadBackSuppressing() {
        preferences.setSuppressingAutomaticDeclarations(true);
        assertTrue(preferences.isSuppressingAutomaticDeclarations());
    }

    @Test
    public void shouldReadBackNotSuppressing() {
        preferences.setSuppressingAutomaticDeclarations(true);
        preferences.setSuppressingAutomaticDeclarations(false);
        assertFalse(preferences.isSuppressingAutomaticDeclarations());
    }

    /**
     * The accessor must read through, so a change made anywhere is seen by the next save without a
     * restart.
     */
    @Test
    public void shouldNotCacheTheStoredValue() {
        assertFalse(preferences.isSuppressingAutomaticDeclarations());
        raw().putBoolean(SUPPRESS_KEY, true);
        assertTrue("the value was cached; a changed preference would need a restart",
                preferences.isSuppressingAutomaticDeclarations());
    }

    @Test
    public void shouldUseADedicatedPreferencesNode() {
        // Sharing a node with the application preferences would let an unrelated clear() wipe this.
        preferences.setSuppressingAutomaticDeclarations(true);
        assertFalse(PreferencesManager.getInstance()
                .getApplicationPreferences(ProtegeApplication.ID)
                .getBoolean(SUPPRESS_KEY, false));
    }

    private org.protege.editor.core.prefs.Preferences raw() {
        return PreferencesManager.getInstance().getApplicationPreferences(PREFERENCES_KEY);
    }
}
