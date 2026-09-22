package org.protege.editor.owl.ui.preferences;

import org.protege.editor.core.ui.preferences.PreferencesLayoutPanel;
import org.protege.editor.owl.model.declaration.EntityDeclarationPreferences;
import org.protege.editor.owl.model.declaration.MisplacedDeclarationPreferences;
import org.protege.editor.owl.model.declaration.OwnershipRule;
import org.protege.editor.owl.model.declaration.OwnershipRuleDisplay;
import org.protege.editor.owl.model.declaration.OwnershipRules;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
            "<html>By default, Protégé adds a declaration axiom for an entity to each "
                    + "ontology in which the entity is not already declared. We strongly "
                    + "recommend keeping this behavior enabled, because declaration axioms "
                    + "make ontology parsing much more robust, particularly when imported "
                    + "ontologies cannot be resolved or loaded.<br><br>"
                    + "You can disable this behavior to prevent declaration axioms from "
                    + "being added automatically.";

    private static final String MISPLACED_GROUP_LABEL = "Misplaced declarations";

    private static final String MISPLACED_HELP_TEXT =
            "Reports when an entity is declared outside the ontology that appears to own its identifier.";

    private final List<OwnershipRule> ownershipRules;

    private final Map<OwnershipRule, JCheckBox> ownershipRuleCheckBoxes = new LinkedHashMap<>();

    private JCheckBox suppressAutomaticEntityDeclarationsCheckBox;

    /** Builds the panel over the registered ownership rules. */
    public AxiomsPreferencesPanel() {
        this(OwnershipRules.registered());
    }

    /**
     * Builds the panel over the given ownership rules.
     *
     * @param ownershipRules the rules to offer, in the order they are consulted
     */
    AxiomsPreferencesPanel(List<OwnershipRule> ownershipRules) {
        this.ownershipRules = ownershipRules;
    }

    /** Builds the panel controls from their stored preferences. */
    public void initialise() throws Exception {
        setLayout(new BorderLayout());
        PreferencesLayoutPanel panel = new PreferencesLayoutPanel();
        add(panel, BorderLayout.NORTH);

        suppressAutomaticEntityDeclarationsCheckBox = new JCheckBox(SUPPRESS_AUTOMATIC_ENTITY_DECLARATIONS_LABEL,
                EntityDeclarationPreferences.getInstance().isSuppressingAutomaticDeclarations());
        suppressAutomaticEntityDeclarationsCheckBox.setToolTipText(SUPPRESS_AUTOMATIC_ENTITY_DECLARATIONS_TOOLTIP);
        // The ownership rules govern what the check reports, and nothing is reported while saving
        // writes the declarations itself. They follow this box rather than the stored setting, so
        // ticking it makes them usable at once.
        suppressAutomaticEntityDeclarationsCheckBox.addItemListener(
                event -> refreshOwnershipRuleAvailability());

        panel.addGroup("Declarations");
        panel.addGroupComponent(suppressAutomaticEntityDeclarationsCheckBox);

        panel.addVerticalPadding();
        panel.addGroup(MISPLACED_GROUP_LABEL);
        MisplacedDeclarationPreferences misplacedPreferences =
                MisplacedDeclarationPreferences.getInstance();
        for (OwnershipRule rule : ownershipRules) {
            OwnershipRuleDisplay display = rule.getDisplay();
            JCheckBox checkBox = new JCheckBox(display.getLabel(), misplacedPreferences.isRuleEnabled(rule));
            checkBox.setToolTipText(display.getTooltipHtmlText());
            ownershipRuleCheckBoxes.put(rule, checkBox);
            panel.addGroupComponent(checkBox);
        }
        panel.addHelpText(MISPLACED_HELP_TEXT);
        refreshOwnershipRuleAvailability();
    }

    /** Stores the values currently shown by the panel. */
    public void applyChanges() {
        EntityDeclarationPreferences.getInstance().setSuppressingAutomaticDeclarations(
                suppressAutomaticEntityDeclarationsCheckBox.isSelected());
        MisplacedDeclarationPreferences misplacedPreferences =
                MisplacedDeclarationPreferences.getInstance();
        ownershipRuleCheckBoxes.forEach(
                (rule, checkBox) -> misplacedPreferences.setRuleEnabled(rule, checkBox.isSelected()));
    }

    /** Ownership rule controls are usable only while automatic declarations are suppressed. */
    private void refreshOwnershipRuleAvailability() {
        boolean checksRun = suppressAutomaticEntityDeclarationsCheckBox.isSelected();
        ownershipRuleCheckBoxes.values().forEach(checkBox -> checkBox.setEnabled(checksRun));
    }

    /** This panel does not hold any resources that need to be released. */
    public void dispose() throws Exception {
    }
}
