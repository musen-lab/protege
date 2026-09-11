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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Checks the automatic declaration checkbox in Preferences &gt; General.
 *
 * <p>A selected checkbox stores {@code true}, which leaves automatic declarations out when saving.
 * A cleared checkbox stores {@code false}, which keeps the default save behavior.
 *
 * <p>In this isolated test, panel setup reaches the checkbox before the later Search section asks
 * for a running editor. The test stops at that point because the checkbox is already ready to use.
 */
public class GeneralPreferencesPanelDeclarations_TestCase {

    private static final String PREFERENCES_KEY = "org.protege.editor.owl.declaration";

    private static final String SUPPRESS_KEY = "suppress.automatic.entity.declarations";

    private boolean hadStoredValue;

    private boolean storedValue;

    @Before
    public void setUp() {
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
                declarationCheckBox().isSelected());
    }

    @Test
    public void shouldShowATickedBoxWhenSuppressing() {
        EntityDeclarationPreferences.getInstance().setSuppressingAutomaticDeclarations(true);
        assertTrue("the preference on should read as a ticked box",
                declarationCheckBox().isSelected());
    }

    /** The checkbox is cleared when no value has been stored. */
    @Test
    public void shouldShowAnUntickedBoxWhenNothingIsStored() {
        assertFalse(declarationCheckBox().isSelected());
    }

    @Test
    public void shouldSuppressWhenTheBoxIsTicked() {
        EntityDeclarationPreferences.getInstance().setSuppressingAutomaticDeclarations(false);
        GeneralPreferencesPanel panel = buildPanel();

        JCheckBox checkBox = declarationCheckBoxOf(panel);
        checkBox.setSelected(true);
        panel.applyChanges();

        assertTrue(EntityDeclarationPreferences.getInstance().isSuppressingAutomaticDeclarations());
    }

    @Test
    public void shouldNotSuppressWhenTheBoxIsCleared() {
        EntityDeclarationPreferences.getInstance().setSuppressingAutomaticDeclarations(true);
        GeneralPreferencesPanel panel = buildPanel();

        JCheckBox checkBox = declarationCheckBoxOf(panel);
        checkBox.setSelected(false);
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

    private JCheckBox declarationCheckBox() {
        return declarationCheckBoxOf(buildPanel());
    }

    private JCheckBox declarationCheckBoxOf(GeneralPreferencesPanel panel) {
        for (JCheckBox checkBox : checkBoxesOf(panel)) {
            if (mentionsDeclarations(checkBox)) {
                return checkBox;
            }
        }
        throw new AssertionError("No declaration control found in the General preferences panel");
    }

    private boolean mentionsDeclarations(JCheckBox checkBox) {
        String text = checkBox.getText();
        return text != null && text.toLowerCase().contains("declaration");
    }

    /** Initializes enough of the panel to create the declaration checkbox. */
    private GeneralPreferencesPanel buildPanel() {
        GeneralPreferencesPanel panel = new GeneralPreferencesPanel();
        try {
            panel.initialise();
        } catch (NullPointerException expected) {
            // Expected when the later Search section requests an editor kit.
        } catch (Exception e) {
            throw new AssertionError("Panel failed before the Search section", e);
        }
        assertNotNull(panel);
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
