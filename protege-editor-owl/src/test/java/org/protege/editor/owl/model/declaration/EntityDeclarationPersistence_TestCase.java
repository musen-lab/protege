package org.protege.editor.owl.model.declaration;

import org.junit.Test;
import org.protege.editor.core.prefs.PreferencesManager;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * Does the preference outlive the process?  Storing it is only half the promise.
 *
 * <p>Run in two separate JVMs, which is the closest an automated test gets to quitting and
 * relaunching Protégé:
 *
 * <pre>
 * mvn test -pl protege-editor-owl -am -Dtest=EntityDeclarationPersistence_TestCase \
 *     -Ddeclarations.persist=write -Dsurefire.failIfNoSpecifiedTests=false
 * mvn test -pl protege-editor-owl -am -Dtest=EntityDeclarationPersistence_TestCase \
 *     -Ddeclarations.persist=read  -Dsurefire.failIfNoSpecifiedTests=false
 * </pre>
 *
 * <p>Both steps skip unless asked for, so an ordinary build never touches stored preferences.  The
 * read step clears what the write step left behind.
 */
public class EntityDeclarationPersistence_TestCase {

    private static final String MODE_PROPERTY = "declarations.persist";

    private static final String PREFERENCES_KEY = "org.protege.editor.owl.declaration";

    @Test
    public void shouldStoreThePreferenceForTheNextProcess() {
        assumeTrue(isMode("write"));
        EntityDeclarationPreferences.getInstance().setSuppressingAutomaticDeclarations(true);
    }

    @Test
    public void shouldReadThePreferenceWrittenByAnEarlierProcess() {
        assumeTrue(isMode("read"));
        try {
            assertTrue("the preference did not survive the process boundary",
                    EntityDeclarationPreferences.getInstance().isSuppressingAutomaticDeclarations());
        } finally {
            PreferencesManager.getInstance().getApplicationPreferences(PREFERENCES_KEY).clear();
        }
    }

    private boolean isMode(String mode) {
        return mode.equals(System.getProperty(MODE_PROPERTY));
    }
}
