package org.protege.editor.owl.model.declaration;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Verifies the user-facing text supplied by every registered {@link OwnershipRule}.
 *
 * <p>A rule supplies only a short label and a longer explanation as plain prose. The preferences
 * panel is responsible for escaping that prose, wrapping it to the available width, and choosing
 * its visual presentation. Keeping markup and line breaks out of the rule makes the same display
 * text safe to use in other renderers.
 *
 * <p>These tests also require the help text to contain more information than the label, so every
 * rule offered in Preferences has a meaningful explanation rather than a repeated control name.
 */
public class OwnershipRuleDisplay_TestCase {

    @Test
    public void shouldSupplyProseForEveryRegisteredRule() {
        for (OwnershipRule rule : OwnershipRules.registered()) {
            assertProse(rule, rule.getDisplay().getLabel(), "label");
            assertProse(rule, rule.getDisplay().getHelpText(), "help text");
        }
    }

    @Test
    public void shouldExplainEveryRegisteredRuleAtGreaterLengthThanItsLabel() {
        for (OwnershipRule rule : OwnershipRules.registered()) {
            OwnershipRuleDisplay display = rule.getDisplay();
            assertTrue(rule.getId() + " explains itself no further than its own label",
                    display.getHelpText().length() > display.getLabel().length());
        }
    }

    /**
     * Checks that one display field is non-blank, single-line prose without markup or entities.
     *
     * @param rule the rule that supplied the wording, used to identify assertion failures
     * @param wording the display text to check
     * @param what the name of the display field, used to identify assertion failures
     */
    private void assertProse(OwnershipRule rule, String wording, String what) {
        String where = rule.getId() + " " + what;
        assertFalse(where + " is blank", wording.trim().isEmpty());
        assertFalse(where + " carries markup", wording.contains("<") || wording.contains(">"));
        assertFalse(where + " carries a character entity", wording.contains("&"));
        assertFalse(where + " carries a line break",
                wording.contains("\n") || wording.contains("\r"));
    }
}
