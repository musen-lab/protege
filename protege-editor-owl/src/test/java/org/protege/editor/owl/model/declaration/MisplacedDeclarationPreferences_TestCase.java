package org.protege.editor.owl.model.declaration;

import com.google.common.collect.ImmutableSet;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.protege.editor.core.prefs.Preferences;
import org.protege.editor.core.prefs.PreferencesManager;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.*;

/**
 * Tests the preferences for misplaced declaration rules.
 */
public class MisplacedDeclarationPreferences_TestCase {

    private static final String PREFERENCES_KEY = "org.protege.editor.owl.declaration";

    private static final String OBO_RULE_KEY = MisplacedDeclarationPreferences.OBO_IDENTIFIER_RULE_KEY;

    private static final String NAMESPACE_RULE_KEY = MisplacedDeclarationPreferences.NAMESPACE_RULE_KEY;

    private static final String SUPPRESS_KEY = "suppress.automatic.entity.declarations";

    private static final String[] KEYS = {OBO_RULE_KEY, NAMESPACE_RULE_KEY, SUPPRESS_KEY};

    private MisplacedDeclarationPreferences preferences;

    private Map<String, Boolean> storedValues;

    @Before
    public void setUp() {
        preferences = MisplacedDeclarationPreferences.getInstance();
        storedValues = new HashMap<>();
        for (String key : KEYS) {
            storedValueOf(key).ifPresent(value -> storedValues.put(key, value));
        }
        raw().clear();
    }

    @After
    public void tearDown() {
        raw().clear();
        storedValues.forEach((key, value) -> raw().putBoolean(key, value));
    }


    @Test
    public void shouldApplyBothRulesWhenNothingIsStored() {
        assertTrue(preferences.isRuleEnabled(OwnershipRule.OBO_IDENTIFIER));
        assertTrue(preferences.isRuleEnabled(OwnershipRule.NAMESPACE));
        assertEquals(ImmutableSet.copyOf(OwnershipRule.values()), preferences.getEnabledRules());
    }

    @Test
    public void shouldNotWriteAValueWhenReadingOne() {
        preferences.getEnabledRules();

        assertFalse(storedValueOf(OBO_RULE_KEY).isPresent());
        assertFalse(storedValueOf(NAMESPACE_RULE_KEY).isPresent());
    }

    @Test
    public void shouldReadBackASwitchedOffRule() {
        preferences.setRuleEnabled(OwnershipRule.OBO_IDENTIFIER, false);

        assertFalse(preferences.isRuleEnabled(OwnershipRule.OBO_IDENTIFIER));
        assertTrue("the other rule is untouched", preferences.isRuleEnabled(OwnershipRule.NAMESPACE));
        assertEquals(ImmutableSet.of(OwnershipRule.NAMESPACE), preferences.getEnabledRules());
    }

    @Test
    public void shouldReadBackARuleSwitchedOffAndOnAgain() {
        preferences.setRuleEnabled(OwnershipRule.NAMESPACE, false);
        preferences.setRuleEnabled(OwnershipRule.NAMESPACE, true);

        assertTrue(preferences.isRuleEnabled(OwnershipRule.NAMESPACE));
    }

    @Test
    public void shouldGiveNoRulesWhenBothAreSwitchedOff() {
        preferences.setRuleEnabled(OwnershipRule.OBO_IDENTIFIER, false);
        preferences.setRuleEnabled(OwnershipRule.NAMESPACE, false);

        assertTrue(preferences.getEnabledRules().isEmpty());
    }

    @Test
    public void shouldNotCacheTheStoredValue() {
        assertTrue(preferences.isRuleEnabled(OwnershipRule.OBO_IDENTIFIER));

        raw().putBoolean(OBO_RULE_KEY, false);

        assertFalse("the value was cached; a changed preference would need a restart",
                preferences.isRuleEnabled(OwnershipRule.OBO_IDENTIFIER));
    }

    @Test
    public void shouldLeaveTheSaveSettingAlone() {
        // The two settings share a preference node but must not read or write each other's key.
        preferences.setRuleEnabled(OwnershipRule.OBO_IDENTIFIER, false);
        preferences.setRuleEnabled(OwnershipRule.NAMESPACE, false);

        assertFalse(EntityDeclarationPreferences.getInstance().isSuppressingAutomaticDeclarations());
        assertFalse(storedValueOf(SUPPRESS_KEY).isPresent());
    }

    /**
     * Reads an optional Boolean preference.
     *
     * @param key the preference key to read
     * @return the stored value, or an empty value if nothing is stored
     */
    private static Optional<Boolean> storedValueOf(String key) {
        boolean withTrueDefault = raw().getBoolean(key, true);
        boolean withFalseDefault = raw().getBoolean(key, false);
        return withTrueDefault == withFalseDefault ? Optional.of(withTrueDefault) : Optional.empty();
    }

    private static Preferences raw() {
        return PreferencesManager.getInstance().getApplicationPreferences(PREFERENCES_KEY);
    }
}
