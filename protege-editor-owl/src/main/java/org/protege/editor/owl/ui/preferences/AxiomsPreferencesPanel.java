package org.protege.editor.owl.ui.preferences;

import com.google.common.html.HtmlEscapers;
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
 * <p>The panel keeps these settings in one place. Each setting controls a separate behavior, and
 * each carries its explanation as help text beneath it rather than on hover.
 *
 * @author Josef Hardi
 */
public class AxiomsPreferencesPanel extends OWLPreferencesPanel {

    private static final String SUPPRESS_AUTOMATIC_ENTITY_DECLARATIONS_LABEL =
            "Suppress automatic entity declarations when saving";

    private static final String SUPPRESS_AUTOMATIC_ENTITY_DECLARATIONS_HELP_TEXT =
            "Prevents Protégé from automatically adding missing entity declarations. "
                    + "Leave this option off unless necessary, because declaration axioms "
                    + "help ontologies load reliably when imports are unavailable.";

    private static final String MISPLACED_GROUP_LABEL = "Misplaced declaration rules";

    /** Sized for the narrowest dialog the application builds, not the default one, and left with
     * enough margin that a longer group heading or a different look and feel cannot overflow it. */
    private static final int HELP_TEXT_WIDTH = 450;

    private final List<OwnershipRule> ownershipRules;

    private final Map<OwnershipRule, JCheckBox> ownershipRuleCheckBoxes = new LinkedHashMap<>();

    private final Map<OwnershipRule, JLabel> ownershipRuleHelpLabels = new LinkedHashMap<>();

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
        // The ownership rules govern what the check reports, and nothing is reported while saving
        // writes the declarations itself. They follow this box rather than the stored setting, so
        // ticking it makes them usable at once.
        suppressAutomaticEntityDeclarationsCheckBox.addItemListener(
                event -> refreshOwnershipRuleAvailability());

        panel.addGroup("Declarations");
        panel.addGroupComponent(suppressAutomaticEntityDeclarationsCheckBox);
        panel.addHelpText(wrapped(SUPPRESS_AUTOMATIC_ENTITY_DECLARATIONS_HELP_TEXT));

        panel.addVerticalPadding();
        panel.addGroup(MISPLACED_GROUP_LABEL);
        MisplacedDeclarationPreferences misplacedPreferences =
                MisplacedDeclarationPreferences.getInstance();
        for (OwnershipRule rule : ownershipRules) {
            OwnershipRuleDisplay display = rule.getDisplay();
            JCheckBox checkBox = new JCheckBox(display.getLabel(), misplacedPreferences.isRuleEnabled(rule));
            ownershipRuleCheckBoxes.put(rule, checkBox);
            panel.addGroupComponent(checkBox);
            ownershipRuleHelpLabels.put(rule, panel.addHelpTextComponent(wrapped(display.getHelpText())));
        }
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

    /** Renders prose as a label that wraps, so that no wording can widen the dialog. The width is
     * deliberately unitless: Swing scales a CSS {@code px} value by 96/72. */
    private static String wrapped(String helpText) {
        return "<html><body style='width:" + HELP_TEXT_WIDTH + "'>"
                + HtmlEscapers.htmlEscaper().escape(helpText)
                + "</body></html>";
    }

    private void refreshOwnershipRuleAvailability() {
        boolean checksRun = suppressAutomaticEntityDeclarationsCheckBox.isSelected();
        ownershipRuleCheckBoxes.values().forEach(checkBox -> checkBox.setEnabled(checksRun));
        ownershipRuleHelpLabels.values().forEach(label -> label.setEnabled(checksRun));
    }

    /** This panel does not hold any resources that need to be released. */
    public void dispose() throws Exception {
    }
}
