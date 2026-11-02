package org.protege.editor.owl.ui.declaration;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;

/** Shared compact two-line presentation for report findings and proposed repairs. */
final class DeclarationRowPanel {

    private final JPanel panel = new JPanel(new BorderLayout(0, 2));
    private final JPanel heading = new JPanel(new FlowLayout(FlowLayout.LEADING, 4, 0));
    private final JLabel action = new JLabel();
    private final JLabel detail = new JLabel();

    DeclarationRowPanel(boolean separator) {
        this(separator, 5);
    }

    DeclarationRowPanel(boolean separator, int verticalPadding) {
        heading.setOpaque(false);
        action.setFont(action.getFont().deriveFont(Font.BOLD));
        detail.setFont(detail.getFont().deriveFont(Math.max(10.0f, detail.getFont().getSize2D() - 1.0f)));
        detail.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
        javax.swing.border.Border padding = BorderFactory.createEmptyBorder(verticalPadding, 10, verticalPadding, 10);
        Color line = UIManager.getColor("Separator.foreground");
        panel.setBorder(separator ? BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, line != null ? line : Color.LIGHT_GRAY), padding) : padding);
        panel.add(heading, BorderLayout.NORTH);
        panel.add(detail, BorderLayout.CENTER);
    }

    Component render(JList<?> list, Component entity, String actionText, String detailText, boolean selected) {
        Color foreground = selected ? list.getSelectionForeground() : list.getForeground();
        panel.setBackground(selected ? list.getSelectionBackground() : list.getBackground());
        heading.removeAll();
        if (actionText != null) {
            action.setText(actionText);
            action.setForeground(foreground);
            heading.add(action);
        }
        entity.setForeground(foreground);
        heading.add(entity);
        detail.setText(detailText);
        int indent = ((FlowLayout) heading.getLayout()).getHgap();
        if (actionText == null && entity instanceof JLabel) {
            JLabel label = (JLabel) entity;
            indent += label.getInsets().left;
            if (label.getIcon() != null) {
                indent += label.getIcon().getIconWidth() + label.getIconTextGap();
            }
        }
        detail.setBorder(BorderFactory.createEmptyBorder(0, indent, 0, 0));
        Color secondary = UIManager.getColor("Label.disabledForeground");
        detail.setForeground(selected ? list.getSelectionForeground() : secondary != null ? secondary : Color.GRAY);
        return panel;
    }
}
