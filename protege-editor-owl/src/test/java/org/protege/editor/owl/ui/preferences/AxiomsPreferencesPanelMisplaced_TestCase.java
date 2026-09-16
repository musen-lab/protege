package org.protege.editor.owl.ui.preferences;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.protege.editor.core.prefs.Preferences;
import org.protege.editor.core.prefs.PreferencesManager;
import org.protege.editor.owl.model.declaration.MisplacedDeclarationPreferences;
import org.protege.editor.owl.model.declaration.OwnershipRule;

import javax.swing.JCheckBox;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests the entity ownership rule options in Preferences &gt; Axioms.
 *
 * <p>Both rules are enabled by default and can be changed independently.
 */
public class AxiomsPreferencesPanelMisplaced_TestCase {

    private static final String PREFERENCES_KEY = "org.protege.editor.owl.declaration";

    private Map<OwnershipRule, Boolean> storedRules;

    @Before
    public void setUp() {
        storedRules = new EnumMap<>(OwnershipRule.class);
        for (OwnershipRule rule : OwnershipRule.values()) {
            storedRules.put(rule, preferences().isRuleEnabled(rule));
        }
        raw().clear();
    }

    @After
    public void tearDown() {
        raw().clear();
        storedRules.forEach((rule, enabled) -> preferences().setRuleEnabled(rule, enabled));
    }

    /** Both rules run by default, so both boxes open ticked. */
    @Test
    public void shouldShowBothBoxesTickedWhenNothingIsStored() {
        AxiomsPreferencesPanel panel = buildPanel();

        assertTrue(oboRuleBoxOf(panel).isSelected());
        assertTrue(namespaceRuleBoxOf(panel).isSelected());
    }

    @Test
    public void shouldShowAnUntickedBoxForASwitchedOffRule() {
        preferences().setRuleEnabled(OwnershipRule.OBO_IDENTIFIER, false);
        AxiomsPreferencesPanel panel = buildPanel();

        assertFalse(oboRuleBoxOf(panel).isSelected());
        assertTrue("the other rule is untouched", namespaceRuleBoxOf(panel).isSelected());
    }

    @Test
    public void shouldSwitchOffTheOboRuleWhenItsBoxIsCleared() {
        AxiomsPreferencesPanel panel = buildPanel();

        oboRuleBoxOf(panel).setSelected(false);
        panel.applyChanges();

        assertFalse(preferences().isRuleEnabled(OwnershipRule.OBO_IDENTIFIER));
        assertTrue(preferences().isRuleEnabled(OwnershipRule.NAMESPACE));
    }

    @Test
    public void shouldSwitchOffTheNamespaceRuleWhenItsBoxIsCleared() {
        AxiomsPreferencesPanel panel = buildPanel();

        namespaceRuleBoxOf(panel).setSelected(false);
        panel.applyChanges();

        assertFalse(preferences().isRuleEnabled(OwnershipRule.NAMESPACE));
        assertTrue(preferences().isRuleEnabled(OwnershipRule.OBO_IDENTIFIER));
    }

    @Test
    public void shouldSwitchARuleBackOn() {
        preferences().setRuleEnabled(OwnershipRule.NAMESPACE, false);
        AxiomsPreferencesPanel panel = buildPanel();

        namespaceRuleBoxOf(panel).setSelected(true);
        panel.applyChanges();

        assertTrue(preferences().isRuleEnabled(OwnershipRule.NAMESPACE));
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
                OwnershipRule.values().length, ownershipBoxes.size());
    }

    @Test
    public void shouldExplainEachRuleInATooltip() {
        AxiomsPreferencesPanel panel = buildPanel();

        assertTrue("the OBO rule needs an example in its tooltip",
                oboRuleBoxOf(panel).getToolTipText().contains("GO_0006915"));
        assertTrue("the namespace rule needs an example in its tooltip",
                namespaceRuleBoxOf(panel).getToolTipText().contains("http://example.org/base"));
    }

    private JCheckBox oboRuleBoxOf(AxiomsPreferencesPanel panel) {
        return boxMentioning(panel, "OBO ID space");
    }

    private JCheckBox namespaceRuleBoxOf(AxiomsPreferencesPanel panel) {
        return boxMentioning(panel, "IRI namespace");
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
        AxiomsPreferencesPanel panel = new AxiomsPreferencesPanel();
        try {
            panel.initialise();
        } catch (Exception e) {
            throw new AssertionError("The Axioms preferences panel failed to build", e);
        }
        return panel;
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
