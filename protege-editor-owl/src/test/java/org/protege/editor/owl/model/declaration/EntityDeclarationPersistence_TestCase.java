package org.protege.editor.owl.model.declaration;

import org.junit.Test;
import org.protege.editor.core.prefs.PreferencesManager;

import java.util.prefs.BackingStoreException;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * Checks that the entity declaration setting written by one Java process can be read by another.
 *
 * <p>This represents closing and restarting Protégé: the first process writes the setting, and the
 * second process reads it from the application's preference storage.
 *
 * <p>Run the write and read steps in separate Java processes:
 *
 * <pre>
 * mvn test -pl protege-editor-owl -am -Dtest=EntityDeclarationPersistence_TestCase \
 *     -Ddeclarations.persist=write -Dsurefire.failIfNoSpecifiedTests=false
 * mvn test -pl protege-editor-owl -am -Dtest=EntityDeclarationPersistence_TestCase \
 *     -Ddeclarations.persist=read  -Dsurefire.failIfNoSpecifiedTests=false
 * </pre>
 *
 * <p>These tests are skipped unless a mode is provided, so a normal build does not change stored
 * preferences. The read step removes the value created by the write step.
 */
public class EntityDeclarationPersistence_TestCase {

    private static final String MODE_PROPERTY = "declarations.persist";

    private static final String PREFERENCES_KEY = "org.protege.editor.owl.declaration";

    private static final String APPLICATION_PREFERENCES = "application_preferences";

    @Test
    public void shouldStoreThePreferenceForTheNextProcess() throws BackingStoreException {
        assumeTrue(isMode("write"));
        EntityDeclarationPreferences.getInstance().setSuppressingAutomaticDeclarations(true);
        // Store the value now because this short-lived process ends before Java's delayed write.
        storedPreferences().flush();
    }

    @Test
    public void shouldReadThePreferenceWrittenByAnEarlierProcess() throws BackingStoreException {
        assumeTrue(isMode("read"));
        try {
            assertTrue("the preference did not survive the process boundary",
                    EntityDeclarationPreferences.getInstance().isSuppressingAutomaticDeclarations());
        } finally {
            PreferencesManager.getInstance().getApplicationPreferences(PREFERENCES_KEY).clear();
            storedPreferences().flush();
        }
    }

    private java.util.prefs.Preferences storedPreferences() {
        return java.util.prefs.Preferences.userRoot()
                .node("PROTEGE_PREFERENCES")
                .node(APPLICATION_PREFERENCES)
                .node(PREFERENCES_KEY);
    }

    private boolean isMode(String mode) {
        return mode.equals(System.getProperty(MODE_PROPERTY));
    }
}
