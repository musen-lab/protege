package org.protege.editor.owl.ui.declaration;

import org.junit.Test;

import javax.swing.*;
import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

import static org.junit.Assert.*;

public class DeclarationRowPanel_TestCase {

    @Test
    public void shouldAlignSuggestionsWithEntityTextForDifferentIconSizesAndSelections() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            DeclarationRowPanel row = new DeclarationRowPanel(false, 2);
            JList<Object> list = new JList<>();
            JLabel entity = new JLabel("Entity label");
            entity.setBorder(BorderFactory.createEmptyBorder(2, 3, 2, 1));
            for (int width : new int[]{12, 24, 0, 16}) {
                entity.setIcon(width == 0 ? null : new ImageIcon(new BufferedImage(width, 16, BufferedImage.TYPE_INT_ARGB)));
                for (boolean selected : new boolean[]{false, true}) {
                    JComponent rendered = (JComponent) row.render(list, entity, null,
                            "Suggested: Add to http://example.org/ontology", selected);
                    rendered.setSize(rendered.getPreferredSize());
                    layOut(rendered);
                    JLabel detail = (JLabel) rendered.getComponent(1);
                    Rectangle entityBounds = SwingUtilities.convertRectangle(entity.getParent(), entity.getBounds(), rendered);
                    Rectangle detailBounds = SwingUtilities.convertRectangle(detail.getParent(), detail.getBounds(), rendered);
                    int entityTextX = entityBounds.x + entity.getInsets().left
                            + (width == 0 ? 0 : width + entity.getIconTextGap());
                    assertEquals(entityTextX, detailBounds.x + detail.getInsets().left);
                    assertEquals(selected ? list.getSelectionBackground() : list.getBackground(), rendered.getBackground());
                    assertTrue(detail.getHeight() >= detail.getPreferredSize().height);
                }
            }
        });
    }

    @Test
    public void shouldKeepPreviewLocationAlignmentWhenAnActionHeadingIsPresent() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            DeclarationRowPanel row = new DeclarationRowPanel(true);
            JList<Object> list = new JList<>();
            JLabel entity = new JLabel("Entity label", new ImageIcon(new BufferedImage(24, 16,
                    BufferedImage.TYPE_INT_ARGB)), SwingConstants.LEADING);
            row.render(list, entity, null, "Suggested: Add to target", false);
            JComponent rendered = (JComponent) row.render(list, entity, "Move declaration:",
                    "From: source -> To: target", false);
            JLabel detail = (JLabel) rendered.getComponent(1);
            assertEquals(4, detail.getInsets().left);
        });
    }

    private static void layOut(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) {
            if (child instanceof Container) {
                layOut((Container) child);
            }
        }
    }
}
