package org.protege.editor.owl.model.declaration;

import com.google.common.collect.ImmutableList;

import javax.annotation.Nonnull;

/**
 * Provides the ordered registry of ownership rules available to the application.
 *
 * <p>Registry order is significant: earlier rules have higher precedence when more than one rule
 * could select an owner for the same entity. The registry currently gives the OBO identifier rule
 * precedence over the more general namespace rule.
 *
 * <p>This registry is the shared source for declaration checking, persisted rule preferences, and
 * the controls shown in the preferences panel. To make a new ownership rule available throughout
 * the application, implement it and add it to the list here in the intended precedence position.
 *
 * @author Josef Hardi
 */
public final class OwnershipRules {

    @Nonnull
    private static final ImmutableList<OwnershipRule> REGISTERED = ImmutableList.of(
            new OboIdentifierRule(),
            new NamespaceRule(),
            new FirstMentionRule());

    private OwnershipRules() {
    }

    /**
     * Returns the immutable list of registered rules in precedence order.
     *
     * @return the registered rules, with the highest-precedence rule first
     */
    @Nonnull
    public static ImmutableList<OwnershipRule> registered() {
        return REGISTERED;
    }
}
