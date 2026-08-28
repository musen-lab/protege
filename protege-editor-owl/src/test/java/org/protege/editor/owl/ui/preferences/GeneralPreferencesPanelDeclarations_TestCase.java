package org.protege.editor.owl.ui.preferences;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.protege.editor.core.prefs.Preferences;
import org.protege.editor.core.prefs.PreferencesManager;
import org.protege.editor.owl.model.declaration.DeclarationSynthesisPreferences;

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
 * The Declarations control in Preferences &gt; General, checked in both directions.
 *
 * <p>The control, the stored preference and the format mapper all read in the same sense - ticked
 * means keep declarations with the ontology that defines the entity - so nothing between the dialog
 * and the save inverts anything.  This test pins that: an inversion creeping in anywhere would
 * silently reverse the setting with nothing visible to show for it.
 *
 * <p>The panel cannot be built completely outside a running application: {@code createUI} reaches for
 * the editor kit when it gets to the Search section, and {@code GeneralPreferencesPanel.main} has
 * always failed there for the same reason.  The Declarations group is built before that point, so
 * this test builds as much of the panel as it can and asserts on the control it finds.
 */
public class GeneralPreferencesPanelDeclarations_TestCase {

    private static final String PREFERENCES_KEY = "org.protege.editor.owl.declaration";

    private static final String KEEP_KEY = "declaration.keep.in.defining.ontology";

    private boolean hadStoredValue;

    private boolean storedValue;

    @Before
    public void setUp() {
        boolean withTrueDefault = raw().getBoolean(KEEP_KEY, true);
        boolean withFalseDefault = raw().getBoolean(KEEP_KEY, false);
        hadStoredValue = withTrueDefault == withFalseDefault;
        storedValue = withTrueDefault;
        raw().clear();
    }

    @After
    public void tearDown() {
        raw().clear();
        if (hadStoredValue) {
            raw().putBoolean(KEEP_KEY, storedValue);
        }
    }

    @Test
    public void shouldShowAnUntickedBoxWhenTheSettingIsOff() {
        DeclarationSynthesisPreferences.getPreferences().setKeepDeclarationsInDefiningOntology(false);
        assertFalse("the setting off should read as an unticked box",
                declarationCheckBox().isSelected());
    }

    @Test
    public void shouldShowATickedBoxWhenTheSettingIsOn() {
        DeclarationSynthesisPreferences.getPreferences().setKeepDeclarationsInDefiningOntology(true);
        assertTrue("the setting on should read as a ticked box",
                declarationCheckBox().isSelected());
    }

    /** A fresh installation shows an unticked box, and writes what Protege has always written. */
    @Test
    public void shouldShowAnUntickedBoxWhenNothingIsStored() {
        assertFalse(declarationCheckBox().isSelected());
    }

    @Test
    public void shouldTurnTheSettingOnWhenTheBoxIsTicked() {
        DeclarationSynthesisPreferences.getPreferences().setKeepDeclarationsInDefiningOntology(false);
        GeneralPreferencesPanel panel = buildPanel();

        JCheckBox checkBox = declarationCheckBoxOf(panel);
        checkBox.setSelected(true);
        panel.applyChanges();

        assertTrue(DeclarationSynthesisPreferences.getPreferences().isKeepDeclarationsInDefiningOntology());
    }

    @Test
    public void shouldTurnTheSettingOffWhenTheBoxIsCleared() {
        DeclarationSynthesisPreferences.getPreferences().setKeepDeclarationsInDefiningOntology(true);
        GeneralPreferencesPanel panel = buildPanel();

        JCheckBox checkBox = declarationCheckBoxOf(panel);
        checkBox.setSelected(false);
        panel.applyChanges();

        assertFalse(DeclarationSynthesisPreferences.getPreferences().isKeepDeclarationsInDefiningOntology());
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
