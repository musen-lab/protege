package org.protege.editor.owl.ui.preferences;

import com.google.common.collect.ImmutableList;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.protege.editor.core.prefs.Preferences;
import org.protege.editor.core.prefs.PreferencesManager;
import org.protege.editor.owl.model.declaration.EntityDeclarationPreferences;
import org.protege.editor.owl.model.declaration.ImportClosureView;
import org.protege.editor.owl.model.declaration.MisplacedDeclarationPreferences;
import org.protege.editor.owl.model.declaration.OwnershipRule;
import org.protege.editor.owl.model.declaration.OwnershipRuleDisplay;
import org.protege.editor.owl.model.declaration.OwnershipRules;

import javax.swing.JCheckBox;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests the entity ownership rule options in Preferences &gt; Axioms.
 *
 * <p>Every registered rule is offered, every rule is enabled by default, and each can be changed
 * independently.
 */
public class AxiomsPreferencesPanelMisplaced_TestCase {

    private static final String PREFERENCES_KEY = "org.protege.editor.owl.declaration";

    private static final String STAND_IN_RULE_KEY = "misplaced.rule.use.stand.in";

    private static final String OBO_RULE_TOOLTIP =
            "<html>Matches the ID prefix in an OBO ID to an ontology's short name. "
                    + "For example, <b>GO_0006915</b> matches <b>go.owl</b>."
                    + "<br><br>This rule is needed for OBO terms because they share the same "
                    + "IRI prefix.</html>";

    private static final String NAMESPACE_RULE_TOOLTIP =
            "<html>Matches an entity's IRI prefix to an ontology IRI. "
                    + "For example, <b>http://example.org/base#Term</b> matches "
                    + "<b>http://example.org/base</b>."
                    + "<br><br>Use this rule when entity IRIs are based on the ontology IRI.</html>";

    private Map<OwnershipRule, Boolean> storedRules;

    private boolean storedSuppression;

    @Before
    public void setUp() {
        storedSuppression = EntityDeclarationPreferences.getInstance().isSuppressingAutomaticDeclarations();
        storedRules = new LinkedHashMap<>();
        for (OwnershipRule rule : OwnershipRules.registered()) {
            storedRules.put(rule, preferences().isRuleEnabled(rule));
        }
        raw().clear();
    }

    @After
    public void tearDown() {
        raw().clear();
        storedRules.forEach((rule, enabled) -> preferences().setRuleEnabled(rule, enabled));
        EntityDeclarationPreferences.getInstance().setSuppressingAutomaticDeclarations(storedSuppression);
    }

    @Test
    public void shouldDisableTheOwnershipControlsWhileSavingWritesDeclarations() {
        EntityDeclarationPreferences.getInstance().setSuppressingAutomaticDeclarations(false);
        AxiomsPreferencesPanel panel = buildPanel();

        assertFalse(oboRuleBoxOf(panel).isEnabled());
        assertFalse(namespaceRuleBoxOf(panel).isEnabled());
        assertTrue("the value it holds is still shown", oboRuleBoxOf(panel).isSelected());
    }

    @Test
    public void shouldEnableTheOwnershipControlsWhenDeclarationsAreSuppressed() {
        EntityDeclarationPreferences.getInstance().setSuppressingAutomaticDeclarations(true);
        AxiomsPreferencesPanel panel = buildPanel();

        assertTrue(oboRuleBoxOf(panel).isEnabled());
        assertTrue(namespaceRuleBoxOf(panel).isEnabled());
    }

    @Test
    public void shouldEnableTheOwnershipControlsAsSoonAsSuppressionIsTicked() {
        EntityDeclarationPreferences.getInstance().setSuppressingAutomaticDeclarations(false);
        AxiomsPreferencesPanel panel = buildPanel();
        assertFalse(oboRuleBoxOf(panel).isEnabled());

        suppressionBoxOf(panel).setSelected(true);

        assertTrue("the controls follow the box rather than the stored setting",
                oboRuleBoxOf(panel).isEnabled());
    }

    @Test
    public void shouldKeepOwnershipValuesWhileTheirControlsAreUnavailable() {
        preferences().setRuleEnabled(oboRule(), false);
        EntityDeclarationPreferences.getInstance().setSuppressingAutomaticDeclarations(false);
        AxiomsPreferencesPanel panel = buildPanel();

        suppressionBoxOf(panel).setSelected(true);
        panel.applyChanges();

        assertFalse("the rule switched off before is still switched off",
                preferences().isRuleEnabled(oboRule()));
    }

    private JCheckBox suppressionBoxOf(AxiomsPreferencesPanel panel) {
        return boxMentioning(panel, "Suppress automatic entity declarations");
    }

    /** Every rule runs by default, so every box opens ticked. */
    @Test
    public void shouldShowEveryBoxTickedWhenNothingIsStored() {
        AxiomsPreferencesPanel panel = buildPanel();

        assertTrue(oboRuleBoxOf(panel).isSelected());
        assertTrue(namespaceRuleBoxOf(panel).isSelected());
    }

    @Test
    public void shouldShowAnUntickedBoxForASwitchedOffRule() {
        preferences().setRuleEnabled(oboRule(), false);
        AxiomsPreferencesPanel panel = buildPanel();

        assertFalse(oboRuleBoxOf(panel).isSelected());
        assertTrue("the other rule is untouched", namespaceRuleBoxOf(panel).isSelected());
    }

    @Test
    public void shouldSwitchOffTheOboRuleWhenItsBoxIsCleared() {
        AxiomsPreferencesPanel panel = buildPanel();

        oboRuleBoxOf(panel).setSelected(false);
        panel.applyChanges();

        assertFalse(preferences().isRuleEnabled(oboRule()));
        assertTrue(preferences().isRuleEnabled(namespaceRule()));
    }

    @Test
    public void shouldSwitchOffTheNamespaceRuleWhenItsBoxIsCleared() {
        AxiomsPreferencesPanel panel = buildPanel();

        namespaceRuleBoxOf(panel).setSelected(false);
        panel.applyChanges();

        assertFalse(preferences().isRuleEnabled(namespaceRule()));
        assertTrue(preferences().isRuleEnabled(oboRule()));
    }

    @Test
    public void shouldSwitchARuleBackOn() {
        preferences().setRuleEnabled(namespaceRule(), false);
        AxiomsPreferencesPanel panel = buildPanel();

        namespaceRuleBoxOf(panel).setSelected(true);
        panel.applyChanges();

        assertTrue(preferences().isRuleEnabled(namespaceRule()));
    }

    @Test
    public void shouldOfferOneControlPerRule() {
        List<JCheckBox> ownershipBoxes = new ArrayList<>();
        for (JCheckBox checkBox : checkBoxesOf(buildPanel())) {
            if (checkBox.getText() != null && checkBox.getText().contains("ownership")) {
                ownershipBoxes.add(checkBox);
            }
        }
        assertEquals("expected one control for each ownership rule",
                OwnershipRules.registered().size(), ownershipBoxes.size());
    }

    @Test
    public void shouldOfferAControlForARuleItWasNotWrittenAgainst() {
        OwnershipRule standIn = standInRule();
        AxiomsPreferencesPanel panel = buildPanel(ImmutableList.of(standIn));

        JCheckBox box = boxMentioning(panel, "Stand-in rule");
        assertTrue("a rule is enabled until something turns it off", box.isSelected());
        assertEquals("<html>Owns nothing.<br><br>Used by tests only.</html>", box.getToolTipText());

        box.setSelected(false);
        panel.applyChanges();

        assertFalse(preferences().isRuleEnabled(standIn));
    }

    @Test
    public void shouldExplainEachRuleInATooltip() {
        AxiomsPreferencesPanel panel = buildPanel();

        assertEquals(OBO_RULE_TOOLTIP, oboRuleBoxOf(panel).getToolTipText());
        assertEquals(NAMESPACE_RULE_TOOLTIP, namespaceRuleBoxOf(panel).getToolTipText());
    }

    private OwnershipRule oboRule() {
        return ruleLabelled("OBO ID");
    }

    private OwnershipRule namespaceRule() {
        return ruleLabelled("IRI prefix");
    }

    private OwnershipRule ruleLabelled(String wanted) {
        for (OwnershipRule rule : OwnershipRules.registered()) {
            if (rule.getDisplay().getLabel().contains(wanted)) {
                return rule;
            }
        }
        throw new AssertionError("No registered rule labelled " + wanted);
    }

    private JCheckBox oboRuleBoxOf(AxiomsPreferencesPanel panel) {
        return boxMentioning(panel, "OBO ID");
    }

    private JCheckBox namespaceRuleBoxOf(AxiomsPreferencesPanel panel) {
        return boxMentioning(panel, "IRI prefix");
    }

    private JCheckBox boxMentioning(AxiomsPreferencesPanel panel, String wanted) {
        for (JCheckBox checkBox : checkBoxesOf(panel)) {
            if (checkBox.getText() != null && checkBox.getText().contains(wanted)) {
                return checkBox;
            }
        }
        throw new AssertionError("No control mentioning " + wanted + " in the Axioms panel");
    }

    private static MisplacedDeclarationPreferences preferences() {
        return MisplacedDeclarationPreferences.getInstance();
    }

    private AxiomsPreferencesPanel buildPanel() {
        return initialised(new AxiomsPreferencesPanel());
    }

    private AxiomsPreferencesPanel buildPanel(List<OwnershipRule> rules) {
        return initialised(new AxiomsPreferencesPanel(rules));
    }

    private AxiomsPreferencesPanel initialised(AxiomsPreferencesPanel panel) {
        try {
            panel.initialise();
        } catch (Exception e) {
            throw new AssertionError("The Axioms preferences panel failed to build", e);
        }
        return panel;
    }

    /** An ownership rule the panel was not written against, standing in for a newly added one. */
    private static OwnershipRule standInRule() {
        return new OwnershipRule() {
            @Override
            public String getId() {
                return STAND_IN_RULE_KEY;
            }

            @Override
            public OwnershipRuleDisplay getDisplay() {
                return OwnershipRuleDisplay.get("Stand-in rule",
                        "<html>Owns nothing.<br><br>Used by tests only.</html>");
            }

            @Override
            public Resolver compile(ImportClosureView closure) {
                return entity -> Optional.empty();
            }
        };
    }

    private List<JCheckBox> checkBoxesOf(Container container) {
        List<JCheckBox> found = new ArrayList<>();
        collectCheckBoxes(container, found);
        return found;
    }

    private void collectCheckBoxes(Container container, List<JCheckBox> found) {
        for (Component child : container.getComponents()) {
            if (child instanceof JCheckBox) {
                found.add((JCheckBox) child);
            }
            if (child instanceof Container) {
                collectCheckBoxes((Container) child, found);
            }
        }
    }


    private static Preferences raw() {
        return PreferencesManager.getInstance().getApplicationPreferences(PREFERENCES_KEY);
    }
}
