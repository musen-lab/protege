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
 * The Declarations control in Preferences &gt; General, driven in both directions.
 *
 * <p>Pins that the control, the stored preference and the save format resolver all read in the same
 * sense: ticked means suppress the automatic entity declarations a save would add.
 *
 * <p>The panel cannot be built completely outside a running application - {@code createUI} reaches
 * for the editor kit at the Search section - so the test builds as much of it as it can and asserts
 * on the control it finds.
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

    /** A fresh installation shows an unticked box, and adds the declarations Protege always adds. */
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

    /**
     * Builds the panel as far as it will go.  The Search section needs a live editor kit and throws;
     * everything before it, the Declarations group included, is fully built by then.
     */
    private GeneralPreferencesPanel buildPanel() {
        GeneralPreferencesPanel panel = new GeneralPreferencesPanel();
        try {
            panel.initialise();
        } catch (NullPointerException expected) {
            // The Search section reaches for an editor kit this test does not have.
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
