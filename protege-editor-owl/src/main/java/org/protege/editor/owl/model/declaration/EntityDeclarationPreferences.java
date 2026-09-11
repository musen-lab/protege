package org.protege.editor.owl.model.declaration;

import org.protege.editor.core.prefs.PreferencesManager;

/**
 * Stores the user's choice about automatic entity declarations.
 *
 * <p>When saving, the OWL API normally declares an entity if the ontology uses it but neither the
 * ontology nor its imports declare it. Enabling this setting prevents those extra declarations
 * from being written. It does not remove declarations that already belong to the ontology.
 *
 * <p>The setting is disabled by default, which keeps the save behavior from before the setting was
 * added.
 *
 * @author Josef Hardi
 */
public class EntityDeclarationPreferences {

    private static final String PREFERENCES_KEY = "org.protege.editor.owl.declaration";

    private static final String SUPPRESS_AUTOMATIC_ENTITY_DECLARATIONS_KEY =
            "suppress.automatic.entity.declarations";

    private static final boolean SUPPRESS_AUTOMATIC_ENTITY_DECLARATIONS_DEFAULT = false;

    private static EntityDeclarationPreferences instance;

    private EntityDeclarationPreferences() {
    }

    public static synchronized EntityDeclarationPreferences getInstance() {
        if (instance == null) {
            instance = new EntityDeclarationPreferences();
        }
        return instance;
    }

    /**
     * @return {@code true} if saving should not add automatic declarations
     */
    public boolean isSuppressingAutomaticDeclarations() {
        return PreferencesManager.getInstance()
                .getApplicationPreferences(PREFERENCES_KEY)
                .getBoolean(SUPPRESS_AUTOMATIC_ENTITY_DECLARATIONS_KEY,
                        SUPPRESS_AUTOMATIC_ENTITY_DECLARATIONS_DEFAULT);
    }

    /**
     * Sets whether saving should leave automatic declarations out.
     *
     * @param suppressAutomaticDeclarations {@code true} to leave automatic declarations out, or
     *                                      {@code false} to include them
     */
    public void setSuppressingAutomaticDeclarations(boolean suppressAutomaticDeclarations) {
        PreferencesManager.getInstance()
                .getApplicationPreferences(PREFERENCES_KEY)
                .putBoolean(SUPPRESS_AUTOMATIC_ENTITY_DECLARATIONS_KEY,
                        suppressAutomaticDeclarations);
    }
}
