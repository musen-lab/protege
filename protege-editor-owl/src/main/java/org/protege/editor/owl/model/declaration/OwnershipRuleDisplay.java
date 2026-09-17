package org.protege.editor.owl.model.declaration;

import com.google.auto.value.AutoValue;

import javax.annotation.Nonnull;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Immutable text that explains an {@link OwnershipRule} to a user.
 *
 * <p>The label names the rule in a control such as a check box, and the tooltip text explains it at
 * whatever length the rule needs. A rule states its own explanation in full rather than filling in
 * a fixed shape, so a rule whose explanation is not a comparison with an example is described just
 * as well as one that is.
 *
 * @author Josef Hardi
 */
@AutoValue
public abstract class OwnershipRuleDisplay {

    /**
     * Creates the text used to present an ownership rule.
     *
     * @param label the short name shown on the rule's user-interface control
     * @param tooltipHtmlText the explanation shown when the control is hovered, as the HTML that
     *                        Swing renders, wrapped in {@code <html>} tags
     * @return an immutable description containing the supplied text
     * @throws NullPointerException if any argument is {@code null}
     */
    @Nonnull
    public static OwnershipRuleDisplay get(@Nonnull String label, @Nonnull String tooltipHtmlText) {
        return new AutoValue_OwnershipRuleDisplay(checkNotNull(label), checkNotNull(tooltipHtmlText));
    }

    /**
     * Returns the short name shown on the rule's user-interface control.
     *
     * @return the rule label
     */
    @Nonnull
    public abstract String getLabel();

    /**
     * Returns the explanation shown when the rule's control is hovered.
     *
     * @return the tooltip text, as HTML
     */
    @Nonnull
    public abstract String getTooltipHtmlText();
}
