package org.protege.editor.owl.model.declaration;

import com.google.common.collect.ImmutableList;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.protege.editor.core.prefs.Preferences;
import org.protege.editor.core.prefs.PreferencesManager;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/**
 * Tests the preferences for misplaced declaration rules.
 */
public class MisplacedDeclarationPreferences_TestCase {

    private static final String PREFERENCES_KEY = "org.protege.editor.owl.declaration";

    private static final String OBO_RULE_KEY = OboIdentifierRule.ID;

    private static final String NAMESPACE_RULE_KEY = NamespaceRule.ID;

    private static final String FIRST_MENTION_RULE_KEY = FirstMentionRule.ID;

    private static final String STAND_IN_RULE_KEY = "misplaced.rule.use.stand.in";

    private static final String SUPPRESS_KEY = "suppress.automatic.entity.declarations";

    private static final String[] KEYS =
            {OBO_RULE_KEY, NAMESPACE_RULE_KEY, FIRST_MENTION_RULE_KEY, STAND_IN_RULE_KEY, SUPPRESS_KEY};

    private static final OwnershipRule OBO_RULE = new OboIdentifierRule();

    private static final OwnershipRule NAMESPACE_RULE = new NamespaceRule();

    private static final OwnershipRule FIRST_MENTION_RULE = new FirstMentionRule();

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
    public void shouldApplyEveryRuleWhenNothingIsStored() {
        assertTrue(preferences.isRuleEnabled(OBO_RULE));
        assertTrue(preferences.isRuleEnabled(NAMESPACE_RULE));
        assertEquals(idsOf(OwnershipRules.registered()), idsOf(preferences.getEnabledRules()));
    }

    @Test
    public void shouldNotWriteAValueWhenReadingOne() {
        preferences.getEnabledRules();

        assertFalse(storedValueOf(OBO_RULE_KEY).isPresent());
        assertFalse(storedValueOf(NAMESPACE_RULE_KEY).isPresent());
    }

    @Test
    public void shouldReadBackASwitchedOffRule() {
        preferences.setRuleEnabled(OBO_RULE, false);

        assertFalse(preferences.isRuleEnabled(OBO_RULE));
        assertTrue("the other rule is untouched", preferences.isRuleEnabled(NAMESPACE_RULE));
        assertEquals(ImmutableList.of(NAMESPACE_RULE_KEY, FIRST_MENTION_RULE_KEY),
                idsOf(preferences.getEnabledRules()));
    }

    @Test
    public void shouldReadBackARuleSwitchedOffAndOnAgain() {
        preferences.setRuleEnabled(NAMESPACE_RULE, false);
        preferences.setRuleEnabled(NAMESPACE_RULE, true);

        assertTrue(preferences.isRuleEnabled(NAMESPACE_RULE));
    }

    @Test
    public void shouldGiveNoRulesWhenEveryRuleIsSwitchedOff() {
        preferences.setRuleEnabled(OBO_RULE, false);
        preferences.setRuleEnabled(NAMESPACE_RULE, false);
        preferences.setRuleEnabled(FIRST_MENTION_RULE, false);

        assertTrue(preferences.getEnabledRules().isEmpty());
    }

    @Test
    public void shouldKeepTheRegisteredOrder() {
        assertEquals("the identifier rules are consulted before the closure rule",
                ImmutableList.of(OBO_RULE_KEY, NAMESPACE_RULE_KEY, FIRST_MENTION_RULE_KEY),
                idsOf(OwnershipRules.registered()));
    }

    @Test
    public void shouldGiveARuleItsOwnSettingWithoutAnyFurtherDeclaration() {
        OwnershipRule standIn = standInRule();

        assertTrue("a rule is enabled until something turns it off", preferences.isRuleEnabled(standIn));
        preferences.setRuleEnabled(standIn, false);

        assertFalse(preferences.isRuleEnabled(standIn));
        assertTrue("the shipped rules are untouched", preferences.isRuleEnabled(OBO_RULE));
        assertEquals(ImmutableList.of(NAMESPACE_RULE_KEY),
                idsOf(preferences.enabledAmong(ImmutableList.of(standIn, NAMESPACE_RULE))));
    }

    @Test
    public void shouldNotCacheTheStoredValue() {
        assertTrue(preferences.isRuleEnabled(OBO_RULE));

        raw().putBoolean(OBO_RULE_KEY, false);

        assertFalse("the value was cached; a changed preference would need a restart",
                preferences.isRuleEnabled(OBO_RULE));
    }

    @Test
    public void shouldLeaveTheSaveSettingAlone() {
        // The two settings share a preference node but must not read or write each other's key.
        preferences.setRuleEnabled(OBO_RULE, false);
        preferences.setRuleEnabled(NAMESPACE_RULE, false);

        assertFalse(EntityDeclarationPreferences.getInstance().isSuppressingAutomaticDeclarations());
        assertFalse(storedValueOf(SUPPRESS_KEY).isPresent());
    }

    private static List<String> idsOf(Collection<OwnershipRule> rules) {
        return rules.stream().map(OwnershipRule::getId).collect(Collectors.toList());
    }

    /** An ownership rule that is not registered, standing in for one a developer has just written. */
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
