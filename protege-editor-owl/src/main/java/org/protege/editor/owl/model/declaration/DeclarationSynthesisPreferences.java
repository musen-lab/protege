package org.protege.editor.owl.model.declaration;

import org.protege.editor.core.prefs.PreferencesManager;

/**
 * Whether entity declarations stay with the ontology that defines the entity.
 *
 * <p>By default Protégé writes an {@code rdf:type} declaration for every entity it finds referenced,
 * whether or not the ontology declares it.  A module of a larger ontology then claims ownership of
 * terms it only borrows.  Turning this preference on stops that: the file gets a declaration only
 * for an entity it declares itself.
 *
 * <p>The flag is stored in the same sense the checkbox reads, so nothing between the dialog and the
 * save has to invert it.  It defaults to {@code false}, which is the behaviour Protégé has always
 * had, so an installation that has never seen the setting writes exactly what it wrote before.
 *
 * @author Josef Hardi
 */
public class DeclarationSynthesisPreferences {

    private static final String PREFERENCES_KEY = "org.protege.editor.owl.declaration";

    private static final String KEEP_DECLARATIONS_IN_DEFINING_ONTOLOGY_KEY =
            "declaration.keep.in.defining.ontology";

    private static final boolean KEEP_DECLARATIONS_IN_DEFINING_ONTOLOGY_DEFAULT = false;

    private static DeclarationSynthesisPreferences instance;

    private DeclarationSynthesisPreferences() {
    }

    public static synchronized DeclarationSynthesisPreferences getPreferences() {
        if (instance == null) {
            instance = new DeclarationSynthesisPreferences();
        }
        return instance;
    }

    /**
     * @return {@code true} if a save should write declarations only for the entities the ontology
     *         defines.  {@code false} when nothing has been stored.
     */
    public boolean isKeepDeclarationsInDefiningOntology() {
        return PreferencesManager.getInstance()
                .getApplicationPreferences(PREFERENCES_KEY)
                .getBoolean(KEEP_DECLARATIONS_IN_DEFINING_ONTOLOGY_KEY,
                        KEEP_DECLARATIONS_IN_DEFINING_ONTOLOGY_DEFAULT);
    }

    public void setKeepDeclarationsInDefiningOntology(boolean keepDeclarationsInDefiningOntology) {
        PreferencesManager.getInstance()
                .getApplicationPreferences(PREFERENCES_KEY)
                .putBoolean(KEEP_DECLARATIONS_IN_DEFINING_ONTOLOGY_KEY,
                        keepDeclarationsInDefiningOntology);
    }
}
