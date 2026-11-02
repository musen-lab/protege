package org.protege.editor.owl.model.declaration;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.protege.editor.core.ProtegeApplication;
import org.protege.editor.core.prefs.PreferencesManager;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Verifies how {@link EntityDeclarationPreferences} stores and reads the user's choice.
 *
 * <p>The tests cover the default value, both stored values, and changes made after an earlier read.
 * They restore the value that was present before each test.
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
        // Opposite default values let the test distinguish a missing value from a stored value.
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

    /** Automatic declarations are included when no value has been stored. */
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

    /** A changed value is returned immediately rather than waiting for a restart. */
    @Test
    public void shouldNotCacheTheStoredValue() {
        assertFalse(preferences.isSuppressingAutomaticDeclarations());
        raw().putBoolean(SUPPRESS_KEY, true);
        assertTrue("the value was cached; a changed preference would need a restart",
                preferences.isSuppressingAutomaticDeclarations());
    }

    @Test
    public void shouldUseADedicatedPreferencesNode() {
        // Keeping this setting separate prevents other settings from clearing its value.
        preferences.setSuppressingAutomaticDeclarations(true);
        assertFalse(PreferencesManager.getInstance()
                .getApplicationPreferences(ProtegeApplication.ID)
                .getBoolean(SUPPRESS_KEY, false));
    }

    private org.protege.editor.core.prefs.Preferences raw() {
        return PreferencesManager.getInstance().getApplicationPreferences(PREFERENCES_KEY);
    }
}
