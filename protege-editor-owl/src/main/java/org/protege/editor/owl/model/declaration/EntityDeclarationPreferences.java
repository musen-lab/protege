package org.protege.editor.owl.model.declaration;

import org.protege.editor.core.prefs.PreferencesManager;

/**
 * Controls whether Protégé automatically adds missing entity declarations when serializing
 * an ontology.
 *
 * <p>By default, Protégé writes an {@code rdf:type} declaration for every named entity when
 * saving an ontology. This preference allows that behavior to be switched off, avoiding
 * redundant declarations for entities referenced from other ontologies.
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
 * @return {@code true} if saving should suppress automatic declarations for entities that belong
 *         to other ontologies; {@code false} if no preference has been stored.
 */
    public boolean isSuppressingAutomaticDeclarations() {
        return PreferencesManager.getInstance()
                .getApplicationPreferences(PREFERENCES_KEY)
                .getBoolean(SUPPRESS_AUTOMATIC_ENTITY_DECLARATIONS_KEY,
                        SUPPRESS_AUTOMATIC_ENTITY_DECLARATIONS_DEFAULT);
    }

    public void setSuppressingAutomaticDeclarations(boolean suppressAutomaticDeclarations) {
        PreferencesManager.getInstance()
                .getApplicationPreferences(PREFERENCES_KEY)
                .putBoolean(SUPPRESS_AUTOMATIC_ENTITY_DECLARATIONS_KEY,
                        suppressAutomaticDeclarations);
    }
}
