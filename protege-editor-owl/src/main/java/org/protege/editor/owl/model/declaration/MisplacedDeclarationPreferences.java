package org.protege.editor.owl.model.declaration;

import com.google.common.collect.ImmutableList;
import org.protege.editor.core.prefs.Preferences;
import org.protege.editor.core.prefs.PreferencesManager;

import javax.annotation.Nonnull;
import java.util.Collection;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Stores which rules are used to find the ontology that owns an entity ID.
 *
 * <p>Each rule is stored under its identifier, so a rule needs no rule key to make it
 * configurable. All rules are enabled by default. Changes apply the next time declarations are
 * checked.
 *
 * @author Josef Hardi
 */
public class MisplacedDeclarationPreferences {

    private static final String PREFERENCES_KEY = "org.protege.editor.owl.declaration";

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
        return preferences().getBoolean(checkNotNull(rule).getId(), RULE_ENABLED_DEFAULT);
    }

    /**
     * Enables or disables an ownership rule.
     *
     * @param rule the rule to change
     * @param enabled {@code true} to enable the rule
     */
    public void setRuleEnabled(@Nonnull OwnershipRule rule, boolean enabled) {
        preferences().putBoolean(checkNotNull(rule).getId(), enabled);
    }

    /**
     * Gets the ownership rules that are enabled, in the order they are consulted.
     *
     * @return the enabled rules
     */
    @Nonnull
    public ImmutableList<OwnershipRule> getEnabledRules() {
        return enabledAmong(OwnershipRules.registered());
    }

    /**
     * Gets the enabled rules among the given ones, in the order they were given.
     *
     * @param rules the rules to filter
     * @return the enabled rules
     */
    @Nonnull
    ImmutableList<OwnershipRule> enabledAmong(@Nonnull Collection<OwnershipRule> rules) {
        checkNotNull(rules);
        ImmutableList.Builder<OwnershipRule> enabled = ImmutableList.builder();
        rules.stream()
            .filter(this::isRuleEnabled)
            .forEach(enabled::add);
        return enabled.build();
    }

    private static Preferences preferences() {
        return PreferencesManager.getInstance().getApplicationPreferences(PREFERENCES_KEY);
    }
}
