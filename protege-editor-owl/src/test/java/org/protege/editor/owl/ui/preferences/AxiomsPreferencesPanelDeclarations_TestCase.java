package org.protege.editor.owl.ui.preferences;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.protege.editor.core.prefs.Preferences;
import org.protege.editor.core.prefs.PreferencesManager;
import org.protege.editor.owl.model.declaration.EntityDeclarationPreferences;

import javax.swing.JCheckBox;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Verifies the automatic declaration checkbox in Preferences &gt; Axioms.
 *
 * <p>When the panel opens, the checkbox reflects the stored preference. Applying the panel stores
 * the current checkbox value. A selected checkbox leaves automatic declarations out when saving;
 * a cleared checkbox keeps the default behavior and includes them.
 *
 * <p>Each test starts without a stored value and restores the value that existed before the test.
 */
public class AxiomsPreferencesPanelDeclarations_TestCase {

    private static final String PREFERENCES_KEY = "org.protege.editor.owl.declaration";

    private static final String SUPPRESS_KEY = "suppress.automatic.entity.declarations";

    private boolean hadStoredValue;

    private boolean storedValue;

    @Before
    public void setUp() {
        // Opposite default values distinguish a missing value from a stored true or false value.
        boolean withTrueDefault = raw().getBoolean(SUPPRESS_KEY, true);
        boolean withFalseDefault = raw().getBoolean(SUPPRESS_KEY, false);
        hadStoredValue = withTrueDefault == withFalseDefault;
        storedValue = withTrueDefault;
        raw().clear();
    }

    @After
    public void tearDown() {
        raw().clear();
        if (hadStoredValue) {
            raw().putBoolean(SUPPRESS_KEY, storedValue);
        }
    }

    @Test
    public void shouldShowAnUntickedBoxWhenNotSuppressing() {
        EntityDeclarationPreferences.getInstance().setSuppressingAutomaticDeclarations(false);
        assertFalse("the preference off should read as an unticked box",
                declarationCheckBoxOf(buildPanel()).isSelected());
    }

    @Test
    public void shouldShowATickedBoxWhenSuppressing() {
        EntityDeclarationPreferences.getInstance().setSuppressingAutomaticDeclarations(true);
        assertTrue("the preference on should read as a ticked box",
                declarationCheckBoxOf(buildPanel()).isSelected());
    }

    /** The checkbox is cleared when no value has been stored. */
    @Test
    public void shouldShowAnUntickedBoxWhenNothingIsStored() {
        assertFalse(declarationCheckBoxOf(buildPanel()).isSelected());
    }

    @Test
    public void shouldSuppressWhenTheBoxIsTicked() {
        EntityDeclarationPreferences.getInstance().setSuppressingAutomaticDeclarations(false);
        AxiomsPreferencesPanel panel = buildPanel();

        declarationCheckBoxOf(panel).setSelected(true);
        panel.applyChanges();

        assertTrue(EntityDeclarationPreferences.getInstance().isSuppressingAutomaticDeclarations());
    }

    @Test
    public void shouldNotSuppressWhenTheBoxIsCleared() {
        EntityDeclarationPreferences.getInstance().setSuppressingAutomaticDeclarations(true);
        AxiomsPreferencesPanel panel = buildPanel();

        declarationCheckBoxOf(panel).setSelected(false);
        panel.applyChanges();

        assertFalse(EntityDeclarationPreferences.getInstance().isSuppressingAutomaticDeclarations());
    }

    @Test
    public void shouldOfferExactlyOneDeclarationControl() {
        List<JCheckBox> matches = new ArrayList<>();
        for (JCheckBox checkBox : checkBoxesOf(buildPanel())) {
            if (mentionsDeclarations(checkBox)) {
                matches.add(checkBox);
            }
        }
        assertEquals("expected a single declaration control", 1, matches.size());
    }

    private JCheckBox declarationCheckBoxOf(AxiomsPreferencesPanel panel) {
        for (JCheckBox checkBox : checkBoxesOf(panel)) {
            if (mentionsDeclarations(checkBox)) {
                return checkBox;
            }
        }
        throw new AssertionError("No declaration control found in the Axioms preferences panel");
    }

    private boolean mentionsDeclarations(JCheckBox checkBox) {
        String text = checkBox.getText();
        return text != null && text.toLowerCase().contains("declaration");
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

    private Preferences raw() {
        return PreferencesManager.getInstance().getApplicationPreferences(PREFERENCES_KEY);
    }
}
