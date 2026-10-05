package org.protege.editor.owl.ui.declaration;

import org.protege.editor.core.ui.list.MList;
import org.protege.editor.core.ui.list.MListItem;
import org.protege.editor.core.ui.list.MListSectionHeader;
import org.protege.editor.core.ui.util.ComponentFactory;
import org.protege.editor.owl.OWLEditorKit;
import org.protege.editor.owl.model.declaration.DeclarationReportOutline;
import org.protege.editor.owl.model.declaration.MisplacedDeclarationFinding;
import org.protege.editor.owl.model.declaration.MissingDeclarationFinding;
import org.protege.editor.owl.ui.renderer.OWLCellRenderer;
import org.semanticweb.owlapi.model.OWLEntity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Presents the result of a manually invoked entity declaration check.
 *
 * <p>The panel mirrors the "Loaded ontology sources" dialog: a resizable plain
 * {@link JOptionPane} hosting an {@link MList} inside a scroll pane. The findings are grouped into
 * the two sections the {@link DeclarationReportOutline} already defines - missing entity
 * declarations and misplaced entity declarations - each introduced by a section header that
 * carries the section count. When a section has no findings it shows an explicit placeholder row,
 * so a clean ontology produces an unmistakable no-issues result rather than a blank list.
 *
 * <p>This view is read-only. It renders the outline it is given and never recomputes findings,
 * changes a preference, or modifies an ontology.
 *
 * @author Josef Hardi
 */
public final class DeclarationCheckReportPanel extends JPanel {

    static final String TITLE = "Entity Declaration Check";

    static final String NO_MISSING_DECLARATIONS = "No missing entity declarations.";

    static final String NO_MISPLACED_DECLARATIONS = "No misplaced entity declarations.";

    private static final String RULE_LABEL_PREFIX = "Ownership rule: ";

    private final OWLEditorKit owlEditorKit;

    DeclarationCheckReportPanel(@Nonnull OWLEditorKit owlEditorKit,
                                @Nonnull DeclarationReportOutline outline) {
        this.owlEditorKit = checkNotNull(owlEditorKit);
        checkNotNull(outline);
        setLayout(new BorderLayout(3, 3));
        MList list = new MList();
        list.setCellRenderer(new FindingCellRenderer());
        list.setListData(rows(outline).toArray());
        JPanel boxHolder = new JPanel(new BorderLayout());
        boxHolder.setOpaque(false);
        boxHolder.add(list, BorderLayout.NORTH);
        add(ComponentFactory.createScrollPane(boxHolder), BorderLayout.CENTER);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(800, 500);
    }

    /**
     * Runs the report for the supplied outline in a resizable dialog centered on the workspace.
     *
     * @param owlEditorKit the editor kit whose workspace parents the dialog
     * @param outline the declaration report outline to present
     * @throws NullPointerException if either argument is {@code null}
     */
    public static void showDialog(@Nonnull OWLEditorKit owlEditorKit,
                                  @Nonnull DeclarationReportOutline outline) {
        DeclarationCheckReportPanel panel = new DeclarationCheckReportPanel(owlEditorKit, outline);
        JOptionPane pane = new JOptionPane(panel, JOptionPane.PLAIN_MESSAGE, JOptionPane.DEFAULT_OPTION);
        JDialog dlg = pane.createDialog(owlEditorKit.getWorkspace(), TITLE);
        dlg.setResizable(true);
        dlg.setVisible(true);
    }

    /**
     * Builds the list rows for an outline: a section header followed by either the section's
     * finding rows or a single placeholder row when the section is empty.
     *
     * @param outline the declaration report outline to lay out
     * @return the ordered list rows, mixing {@link MListSectionHeader} and {@link MListItem}
     * @throws NullPointerException if {@code outline} is {@code null}
     */
    @Nonnull
    static List<Object> rows(@Nonnull DeclarationReportOutline outline) {
        checkNotNull(outline);
        List<Object> rows = new ArrayList<>();
        rows.add(new SectionHeader(outline.getMissingSection().getHeading()));
        if (outline.getMissing().isEmpty()) {
            rows.add(new MessageRow(NO_MISSING_DECLARATIONS));
        } else {
            for (MissingDeclarationFinding finding : outline.getMissing()) {
                rows.add(new FindingRow(finding.getEntity(), null));
            }
        }
        rows.add(new SectionHeader(outline.getMisplacedSection().getHeading()));
        if (outline.getMisplaced().isEmpty()) {
            rows.add(new MessageRow(NO_MISPLACED_DECLARATIONS));
        } else {
            for (MisplacedDeclarationFinding finding : outline.getMisplaced()) {
                rows.add(new FindingRow(finding.getEntity(),
                        RULE_LABEL_PREFIX + DeclarationReportOutline.ruleLabelOf(finding)));
            }
        }
        return rows;
    }

    /**
     * Renders finding rows with the shared OWL cell renderer and plain message rows with a label.
     */
    private final class FindingCellRenderer implements ListCellRenderer {

        private final OWLCellRenderer owlCellRenderer = new OWLCellRenderer(owlEditorKit);

        private final JPanel findingPanel = new JPanel(new BorderLayout(0, 1));

        private final JLabel detailLabel = new JLabel();

        private final JLabel messageLabel = new JLabel();

        private FindingCellRenderer() {
            detailLabel.setForeground(Color.DARK_GRAY);
            detailLabel.setFont(detailLabel.getFont().deriveFont(12.0f));
            detailLabel.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 0));
        }

        @Override
        public Component getListCellRendererComponent(JList list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            if (value instanceof MessageRow) {
                messageLabel.setText(((MessageRow) value).getText());
                messageLabel.setOpaque(true);
                messageLabel.setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
                return messageLabel;
            }
            FindingRow row = (FindingRow) value;
            Component entityComponent = owlCellRenderer.getListCellRendererComponent(
                    list, row.getEntity(), index, isSelected, cellHasFocus);
            findingPanel.removeAll();
            findingPanel.setOpaque(true);
            findingPanel.setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
            findingPanel.add(entityComponent, BorderLayout.CENTER);
            if (row.getDetail() != null) {
                detailLabel.setText(row.getDetail());
                findingPanel.add(detailLabel, BorderLayout.SOUTH);
            }
            return findingPanel;
        }
    }

    /** A non-interactive section header carrying the outline's section heading. */
    static final class SectionHeader implements MListSectionHeader {

        private final String name;

        SectionHeader(@Nonnull String name) {
            this.name = checkNotNull(name);
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean canAdd() {
            return false;
        }
    }

    /** A read-only row for one entity finding. */
    static final class FindingRow implements MListItem {

        @Nonnull
        private final OWLEntity entity;

        @Nullable
        private final String detail;

        FindingRow(@Nonnull OWLEntity entity, @Nullable String detail) {
            this.entity = checkNotNull(entity);
            this.detail = detail;
        }

        @Nonnull
        OWLEntity getEntity() {
            return entity;
        }

        @Nullable
        String getDetail() {
            return detail;
        }

        @Override
        public boolean isEditable() {
            return false;
        }

        @Override
        public void handleEdit() {
            // read-only
        }

        @Override
        public boolean isDeleteable() {
            return false;
        }

        @Override
        public boolean handleDelete() {
            return false;
        }

        @Override
        public String getTooltip() {
            return entity.getIRI().toString();
        }
    }

    /** A read-only row presenting a plain message, such as an empty-section placeholder. */
    static final class MessageRow implements MListItem {

        @Nonnull
        private final String text;

        MessageRow(@Nonnull String text) {
            this.text = checkNotNull(text);
        }

        @Nonnull
        String getText() {
            return text;
        }

        @Override
        public boolean isEditable() {
            return false;
        }

        @Override
        public void handleEdit() {
            // read-only
        }

        @Override
        public boolean isDeleteable() {
            return false;
        }

        @Override
        public boolean handleDelete() {
            return false;
        }

        @Override
        public String getTooltip() {
            return null;
        }
    }
}
