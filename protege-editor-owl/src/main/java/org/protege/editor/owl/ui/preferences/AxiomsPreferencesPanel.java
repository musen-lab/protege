package org.protege.editor.owl.ui.preferences;

import org.protege.editor.core.ui.preferences.PreferencesLayoutPanel;
import org.protege.editor.owl.model.declaration.EntityDeclarationPreferences;

import javax.swing.*;
import java.awt.*;

/**
 * Displays axiom-related settings in Preferences.
 *
 * <p>The panel keeps these settings in one place. Each setting controls a separate behavior.
 *
 * @author Josef Hardi
 */
public class AxiomsPreferencesPanel extends OWLPreferencesPanel {

    private static final String SUPPRESS_AUTOMATIC_ENTITY_DECLARATIONS_LABEL =
            "Suppress automatic entity declarations when saving";

    private static final String SUPPRESS_AUTOMATIC_ENTITY_DECLARATIONS_TOOLTIP =
            "<html>By default, saving adds declarations for entities that are used but not "
                    + "declared in the ontology or its imports.<br><br>"
                    + "Select this option to leave those automatic declarations out. "
                    + "Declarations already present in the ontology are still saved.<br><br>"
                    + "This setting applies to RDF/XML, Turtle, OWL/XML, and Functional Syntax.";

    private JCheckBox suppressAutomaticEntityDeclarationsCheckBox;

    /** Builds the panel controls from their stored preferences. */
    public void initialise() throws Exception {
        setLayout(new BorderLayout());
        PreferencesLayoutPanel panel = new PreferencesLayoutPanel();
        add(panel, BorderLayout.NORTH);

        suppressAutomaticEntityDeclarationsCheckBox = new JCheckBox(SUPPRESS_AUTOMATIC_ENTITY_DECLARATIONS_LABEL,
                EntityDeclarationPreferences.getInstance().isSuppressingAutomaticDeclarations());
        suppressAutomaticEntityDeclarationsCheckBox.setToolTipText(SUPPRESS_AUTOMATIC_ENTITY_DECLARATIONS_TOOLTIP);

        panel.addGroup("Declarations");
        panel.addGroupComponent(suppressAutomaticEntityDeclarationsCheckBox);
    }

    /** Stores the values currently shown by the panel. */
    public void applyChanges() {
        EntityDeclarationPreferences.getInstance().setSuppressingAutomaticDeclarations(
                suppressAutomaticEntityDeclarationsCheckBox.isSelected());
    }

    /** This panel does not hold any resources that need to be released. */
    public void dispose() throws Exception {
    }
}
