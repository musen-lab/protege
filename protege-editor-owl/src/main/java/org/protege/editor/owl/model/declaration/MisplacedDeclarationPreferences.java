package org.protege.editor.owl.model.declaration;

import com.google.common.collect.ImmutableSet;
import org.protege.editor.core.prefs.Preferences;
import org.protege.editor.core.prefs.PreferencesManager;

import javax.annotation.Nonnull;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Stores which rules are used to find the ontology that owns an entity ID.
 *
 * <p>All rules are enabled by default. Changes apply the next time declarations are checked.
 *
 * @author Josef Hardi
 */
public class MisplacedDeclarationPreferences {

    private static final String PREFERENCES_KEY = "org.protege.editor.owl.declaration";

    static final String OBO_IDENTIFIER_RULE_KEY = "misplaced.rule.use.obo.identifier";

    static final String NAMESPACE_RULE_KEY = "misplaced.rule.use.namespace";

    private static final boolean RULE_ENABLED_DEFAULT = true;

    private static MisplacedDeclarationPreferences instance;

    private MisplacedDeclarationPreferences() {
    }

    /**
     * Gets the shared preferences instance.
     *
     * @return the preferences instance
     */
    public static synchronized MisplacedDeclarationPreferences getInstance() {
        if (instance == null) {
            instance = new MisplacedDeclarationPreferences();
        }
        return instance;
    }

    /**
     * Checks whether an ownership rule is enabled.
     *
     * @param rule the rule to check
     * @return {@code true} if the rule is enabled
     */
    public boolean isRuleEnabled(@Nonnull OwnershipRule rule) {
        return preferences().getBoolean(keyFor(rule), RULE_ENABLED_DEFAULT);
    }

    /**
     * Enables or disables an ownership rule.
     *
     * @param rule the rule to change
     * @param enabled {@code true} to enable the rule
     */
    public void setRuleEnabled(@Nonnull OwnershipRule rule, boolean enabled) {
        preferences().putBoolean(keyFor(rule), enabled);
    }

    /**
     * Gets the enabled ownership rules.
     *
     * @return the enabled rules
     */
    @Nonnull
    public ImmutableSet<OwnershipRule> getEnabledRules() {
        ImmutableSet.Builder<OwnershipRule> enabled = ImmutableSet.builder();
        for (OwnershipRule rule : OwnershipRule.values()) {
            if (isRuleEnabled(rule)) {
                enabled.add(rule);
            }
        }
        return enabled.build();
    }

    private static String keyFor(OwnershipRule rule) {
        switch (checkNotNull(rule)) {
            case OBO_IDENTIFIER:
                return OBO_IDENTIFIER_RULE_KEY;
            case NAMESPACE:
                return NAMESPACE_RULE_KEY;
            default:
                throw new IllegalArgumentException("Unknown ownership rule: " + rule);
        }
    }

    private static Preferences preferences() {
        return PreferencesManager.getInstance().getApplicationPreferences(PREFERENCES_KEY);
    }
}
