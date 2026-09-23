package org.protege.editor.owl.ui.preferences;

import com.google.common.collect.ImmutableList;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.protege.editor.core.prefs.Preferences;
import org.protege.editor.core.prefs.PreferencesManager;
import org.protege.editor.core.ui.preferences.PreferencesDialogPanel;
import org.protege.editor.owl.model.declaration.EntityDeclarationPreferences;
import org.protege.editor.owl.model.declaration.ImportClosureView;
import org.protege.editor.owl.model.declaration.MisplacedDeclarationPreferences;
import org.protege.editor.owl.model.declaration.OwnershipRule;
import org.protege.editor.owl.model.declaration.OwnershipRuleDisplay;
import org.protege.editor.owl.model.declaration.OwnershipRules;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
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

    private static final String OBO_RULE_KEY = "misplaced.rule.use.obo.identifier";

    private static final String NAMESPACE_RULE_KEY = "misplaced.rule.use.namespace";

    private static final String STAND_IN_RULE_HELP_TEXT = "Owns nothing. Used by tests only.";

    private static final String MISPLACED_GROUP_LABEL = "Misplaced declaration rules";

    private static final String OBO_RULE_LABEL = "Decide ownership from the OBO ID";

    private static final String NAMESPACE_RULE_LABEL = "Decide ownership from the IRI prefix";

    // Fragments rather than whole sentences: the wording is escaped before it is shown, and an
    // apostrophe is escaped along with the rest, so only these parts survive verbatim.
    private static final String OBO_RULE_HELP_TEXT_FRAGMENT =
            "s short name: GO_0006915 matches go.owl.";

    private static final String NAMESPACE_RULE_HELP_TEXT_FRAGMENT =
            "IRI prefix to an ontology IRI: http://example.org/base#Term "
                    + "matches http://example.org/base.";

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
        AxiomsPreferencesPanel panel = buildPanel();
        for (OwnershipRule rule : OwnershipRules.registered()) {
            boxFor(panel, rule);
        }
        assertEquals("expected one control for each ownership rule, and no other",
                OwnershipRules.registered().size(), ownershipBoxesOf(panel).size());
    }

    /** The controls carrying a registered rule's label, which excludes the suppression box. */
    private List<JCheckBox> ownershipBoxesOf(AxiomsPreferencesPanel panel) {
        Set<String> ruleLabels = new HashSet<>();
        for (OwnershipRule rule : OwnershipRules.registered()) {
            ruleLabels.add(rule.getDisplay().getLabel());
        }
        List<JCheckBox> found = new ArrayList<>();
        for (JCheckBox checkBox : checkBoxesOf(panel)) {
            if (ruleLabels.contains(checkBox.getText())) {
                found.add(checkBox);
            }
        }
        return found;
    }

    @Test
    public void shouldOfferAControlForARuleItWasNotWrittenAgainst() {
        OwnershipRule standIn = standInRule();
        AxiomsPreferencesPanel panel = buildPanel(ImmutableList.of(standIn));

        JCheckBox box = boxMentioning(panel, "Stand-in rule");
        assertTrue("a rule is enabled until something turns it off", box.isSelected());
        assertShows(STAND_IN_RULE_HELP_TEXT, helpLabelFor(panel, standIn));

        box.setSelected(false);
        panel.applyChanges();

        assertFalse(preferences().isRuleEnabled(standIn));
    }

    @Test
    public void shouldCarryTheWordingEachRuleSupplies() {
        AxiomsPreferencesPanel panel = buildPanel();

        assertEquals(OBO_RULE_LABEL, oboRuleBoxOf(panel).getText());
        assertEquals(NAMESPACE_RULE_LABEL, namespaceRuleBoxOf(panel).getText());
        assertShows(OBO_RULE_HELP_TEXT_FRAGMENT, helpLabelFor(panel, oboRule()));
        assertShows(NAMESPACE_RULE_HELP_TEXT_FRAGMENT, helpLabelFor(panel, namespaceRule()));
    }

    /** Each rule is explained beneath its own control, and explained nowhere else. */
    @Test
    public void shouldExplainEachRuleBeneathItsControl() {
        AxiomsPreferencesPanel panel = buildPanel();

        for (OwnershipRule rule : OwnershipRules.registered()) {
            JCheckBox box = boxFor(panel, rule);
            List<Component> order = componentsOf(panel);
            int next = order.indexOf(box) + 1;
            assertTrue(rule.getId() + " has nothing after its control", next < order.size());
            assertSame(rule.getId() + " is not explained immediately beneath its control",
                    helpLabelFor(panel, rule), order.get(next));
        }
    }

    /**
     * The group heading names the rules, so the group carries no explanation of its own.
     *
     * <p>One styled exactly as the rule explanations would read as belonging to the first rule
     * rather than to the group.
     */
    @Test
    public void shouldPutNothingBetweenTheHeadingAndTheFirstRule() {
        AxiomsPreferencesPanel panel = buildPanel();
        List<Component> order = componentsOf(panel);

        JCheckBox firstRuleBox = boxFor(panel, OwnershipRules.registered().get(0));
        JLabel suppressionHelp = helpLabelForSuppression(panel);
        for (int i = order.indexOf(suppressionHelp) + 1; i < order.indexOf(firstRuleBox); i++) {
            if (!(order.get(i) instanceof JLabel)) {
                continue;
            }
            // The heading is itself a label, and is the only one this stretch may hold.
            assertEquals("something is explained between the group heading and its first rule",
                    MISPLACED_GROUP_LABEL, ((JLabel) order.get(i)).getText());
        }
    }

    private JLabel helpLabelForSuppression(AxiomsPreferencesPanel panel) {
        List<Component> order = componentsOf(panel);
        for (int i = order.indexOf(suppressionBoxOf(panel)) + 1; i < order.size(); i++) {
            if (order.get(i) instanceof JLabel) {
                return (JLabel) order.get(i);
            }
        }
        throw new AssertionError("Nothing explains the suppression control in the Axioms panel");
    }

    @Test
    public void shouldExplainNoRuleOnHover() {
        AxiomsPreferencesPanel panel = buildPanel();

        for (OwnershipRule rule : OwnershipRules.registered()) {
            assertNull(rule.getId() + " still explains itself on hover",
                    boxFor(panel, rule).getToolTipText());
        }
    }

    @Test
    public void shouldWrapEveryExplanationItShows() {
        AxiomsPreferencesPanel panel = buildPanel();

        for (OwnershipRule rule : OwnershipRules.registered()) {
            String shown = helpLabelFor(panel, rule).getText();
            assertTrue(rule.getId() + " is shown as text that cannot wrap: " + shown,
                    shown.startsWith("<html><body style=") && shown.contains("width:"));
        }
    }

    /** Wording is escaped, so a rule that mentions a tag has its text shown rather than obeyed. */
    @Test
    public void shouldShowWordingThatLooksLikeMarkupAsWording() {
        OwnershipRule standIn = standInRule("Owns <b>nothing</b> & explains why.");
        AxiomsPreferencesPanel panel = buildPanel(ImmutableList.of(standIn));

        String shown = helpLabelFor(panel, standIn).getText();
        assertTrue("an angle bracket in the wording was not escaped: " + shown,
                shown.contains("&lt;b&gt;nothing&lt;/b&gt;"));
        assertTrue("an ampersand in the wording was not escaped: " + shown,
                shown.contains("&amp;"));
    }

    /**
     * A rule's wording is edited without the layout being reconsidered, so length cannot overflow.
     *
     * <p>The panel sits in a scroll pane, so an explanation too wide to fit does not stretch the
     * dialog. It makes the dialog scroll sideways, which is worse.
     */
    @Test
    public void shouldWrapAnExplanationTooLongToFit() {
        StringBuilder wording = new StringBuilder("Owns everything.");
        while (wording.length() < 600) {
            wording.append(" It was given wording nobody thought to shorten.");
        }
        OwnershipRule standIn = standInRule(wording.toString());

        AxiomsPreferencesPanel panel = buildPanel(ImmutableList.of(standIn));

        assertTrue("an explanation of " + wording.length() + " characters widened the panel to "
                        + panel.getPreferredSize().width + ", past the width of the dialog holding it",
                panel.getPreferredSize().width <= PreferencesDialogPanel.DIALOG_DEFAULT_WIDTH);
    }

    @Test
    public void shouldDimEachRuleExplanationWithItsControl() {
        EntityDeclarationPreferences.getInstance().setSuppressingAutomaticDeclarations(false);
        AxiomsPreferencesPanel panel = buildPanel();

        for (OwnershipRule rule : OwnershipRules.registered()) {
            assertFalse(rule.getId() + " is explained as though it applied",
                    helpLabelFor(panel, rule).isEnabled());
        }

        suppressionBoxOf(panel).setSelected(true);

        for (OwnershipRule rule : OwnershipRules.registered()) {
            assertTrue(rule.getId() + " is still explained as though it did not apply",
                    helpLabelFor(panel, rule).isEnabled());
        }
    }

    private OwnershipRule oboRule() {
        return ruleIdentifiedBy(OBO_RULE_KEY);
    }

    private OwnershipRule namespaceRule() {
        return ruleIdentifiedBy(NAMESPACE_RULE_KEY);
    }

    /**
     * Finds a registered rule by the identifier it promises to keep across releases.
     *
     * <p>A rule is named here by identifier rather than by label so that rewording a label fails
     * only the test that asserts the wording.
     */
    private OwnershipRule ruleIdentifiedBy(String wanted) {
        for (OwnershipRule rule : OwnershipRules.registered()) {
            if (rule.getId().equals(wanted)) {
                return rule;
            }
        }
        throw new AssertionError("No registered rule identified by " + wanted);
    }

    private JCheckBox oboRuleBoxOf(AxiomsPreferencesPanel panel) {
        return boxFor(panel, oboRule());
    }

    private JCheckBox namespaceRuleBoxOf(AxiomsPreferencesPanel panel) {
        return boxFor(panel, namespaceRule());
    }

    /** Finds a rule's control through the label that rule itself supplies. */
    private JCheckBox boxFor(AxiomsPreferencesPanel panel, OwnershipRule rule) {
        return boxMentioning(panel, rule.getDisplay().getLabel());
    }

    /** The label that follows a rule's control, which is where that rule is explained. */
    private JLabel helpLabelFor(AxiomsPreferencesPanel panel, OwnershipRule rule) {
        List<Component> order = componentsOf(panel);
        for (int i = order.indexOf(boxFor(panel, rule)) + 1; i < order.size(); i++) {
            if (order.get(i) instanceof JLabel) {
                return (JLabel) order.get(i);
            }
        }
        throw new AssertionError("Nothing explains " + rule.getId() + " in the Axioms panel");
    }

    private void assertShows(String wanted, JLabel label) {
        assertTrue("expected to read " + wanted + " but read " + label.getText(),
                label.getText().contains(wanted));
    }

    /** Every component of the panel, in the order the panel added them. */
    private List<Component> componentsOf(Container container) {
        List<Component> found = new ArrayList<>();
        collectComponents(container, found);
        return found;
    }

    private void collectComponents(Container container, List<Component> found) {
        for (Component child : container.getComponents()) {
            found.add(child);
            if (child instanceof Container) {
                collectComponents((Container) child, found);
            }
        }
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
        return standInRule(STAND_IN_RULE_HELP_TEXT);
    }

    private static OwnershipRule standInRule(String helpText) {
        return new OwnershipRule() {
            @Override
            public String getId() {
                return STAND_IN_RULE_KEY;
            }

            @Override
            public OwnershipRuleDisplay getDisplay() {
                return OwnershipRuleDisplay.get("Stand-in rule", helpText);
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
