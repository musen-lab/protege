package org.protege.editor.owl.ui.declaration;

import com.google.common.collect.ImmutableList;
import org.protege.editor.core.ui.list.MList;
import org.protege.editor.core.ui.list.MListItem;
import org.protege.editor.core.ui.list.MListSectionHeader;
import org.protege.editor.core.ui.util.ComponentFactory;
import org.protege.editor.owl.OWLEditorKit;
import org.protege.editor.owl.model.declaration.DeclarationReportOutline;
import org.protege.editor.owl.model.declaration.MisplacedDeclarationFinding;
import org.protege.editor.owl.model.declaration.MissingDeclarationFinding;
import org.protege.editor.owl.model.declaration.OntologyIdFormat;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyID;

import javax.annotation.Nonnull;
import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Presents the entity declaration check result from invoking the Tools menu item.
 *
 * <p>The findings are grouped into the two sections the {@link DeclarationReportOutline}
 * already defines - missing entity declarations and misplaced entity declarations -
 * each introduced by a section header that carries the section count. When a section has
 * no findings it shows an explicit placeholder row, so a clean ontology produces an
 * unmistakable no-issues result rather than a blank list.
 *
 * @author Josef Hardi
 */
public final class DeclarationCheckReportPanel extends JPanel {

    static final String TITLE = "Entity Declaration Check";

    static final String NO_MISSING_DECLARATIONS = "No missing entity declarations.";

    static final String NO_MISPLACED_DECLARATIONS = "No misplaced entity declarations.";

    private final MList list = new MList() {
        @Override
        protected javax.swing.border.Border createListItemBorder(JList list, Object value, int index,
                                                                  boolean selected, boolean focused) {
            // The default MList border reserves an unselected 20-pixel gutter on the left.
            return BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(240, 240, 240)),
                    BorderFactory.createEmptyBorder(1, 0, 1, 0));
        }
    };

    private DeclarationResolutionActions resolutionActions;

    private DeclarationCheckReportPanel(OWLEditorKit owlEditorKit, List<Object> reportRows) {
        this(DeclarationDestinationPanel.entityRenderer(checkNotNull(owlEditorKit)), reportRows);
        OWLOntology root = owlEditorKit.getOWLModelManager().getActiveOntology();
        ListCellRenderer<OWLEntity> entityRenderer = DeclarationDestinationPanel.entityRenderer(owlEditorKit);
        resolutionActions = new DeclarationResolutionActions(
                owlEditorKit.getOWLModelManager(), root, list,
                updated -> list.setListData(updated.toArray()),
                new DeclarationResolutionDialogs(this, entityRenderer));
        add(resolutionActions.getToolbar(), BorderLayout.SOUTH);
    }

    /** Lays out precomputed report rows with the supplied entity renderer. */
    DeclarationCheckReportPanel(ListCellRenderer entityRenderer, List<Object> reportRows) {
        checkNotNull(entityRenderer);
        setLayout(new BorderLayout(3, 3));
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        list.setCellRenderer(new FindingCellRenderer(entityRenderer));
        list.setListData(reportRows.toArray());
        JPanel boxHolder = new ViewportWidthPanel();
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
        showDialog(owlEditorKit, rows(outline));
    }

    /** Shows precomputed rows so the Tools action need not scan large ontologies on the UI thread. */
    static void showDialog(OWLEditorKit owlEditorKit, List<Object> reportRows) {
        DeclarationCheckReportPanel panel = new DeclarationCheckReportPanel(owlEditorKit, reportRows);
        JOptionPane pane = new JOptionPane(panel, JOptionPane.PLAIN_MESSAGE, JOptionPane.DEFAULT_OPTION);
        JDialog dlg = pane.createDialog(owlEditorKit.getWorkspace(), TITLE);
        dlg.setResizable(true);
        try {
            dlg.setVisible(true);
        } finally {
            if (panel.resolutionActions != null) {
                panel.resolutionActions.dispose();
            }
            dlg.dispose();
        }
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
                rows.add(new FindingRow(finding, finding.getEntity(),
                        missingDetail(finding.getPlacementOntology())));
            }
        }
        rows.add(new SectionHeader(outline.getMisplacedSection().getHeading()));
        if (outline.getMisplaced().isEmpty()) {
            rows.add(new MessageRow(NO_MISPLACED_DECLARATIONS));
        } else {
            for (MisplacedDeclarationFinding finding : outline.getMisplaced()) {
                rows.add(new FindingRow(finding, finding.getEntity(), misplacedDetail(finding)));
            }
        }
        return rows;
    }

    @Nonnull
    private static List<String> missingDetail(@Nonnull Optional<OWLOntologyID> suggestion) {
        return ImmutableList.of(suggestion
                .map(id -> "Suggested: Add the declaration axiom to " + OntologyIdFormat.format(id))
                .orElse("Suggested: No unique location found; choose one when resolving."));
    }

    @Nonnull
    private static List<String> misplacedDetail(@Nonnull MisplacedDeclarationFinding finding) {
        return ImmutableList.of(
                "Suggested: Move the declaration axiom to " + OntologyIdFormat.format(finding.getOwningOntology())
                + " (based on: " + DeclarationReportOutline.shortRuleLabelOf(finding) + ")");
    }

    /**
     * Fills the viewport when content fits and allows horizontal scrolling for full detail IRIs.
     */
    private static final class ViewportWidthPanel extends JPanel implements Scrollable {

        private ViewportWidthPanel() {
            super(new BorderLayout());
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 10;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return orientation == SwingConstants.VERTICAL ? visibleRect.height : visibleRect.width;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            // Grow only to the content's natural width; long details use horizontal scrolling.
            return getParent() instanceof JViewport
                    && getPreferredSize().width <= ((JViewport) getParent()).getExtentSize().width;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    /**
     * Renders finding rows with the entity renderer above one detail line per field, and plain
     * message rows with a label.
     */
    private static final class FindingCellRenderer implements ListCellRenderer {

        private final ListCellRenderer entityRenderer;

        private final DeclarationRowPanel findingPanel = new DeclarationRowPanel(false, 2);

        // MList replaces its renderer's border; keep the shared row padding inside a holder.
        private final JPanel findingHolder = new JPanel(new BorderLayout());

        private final JLabel messageLabel = new JLabel();

        private FindingCellRenderer(@Nonnull ListCellRenderer entityRenderer) {
            this.entityRenderer = checkNotNull(entityRenderer);
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
            Component entityComponent = entityRenderer.getListCellRendererComponent(
                    list, row.getEntity(), index, isSelected, cellHasFocus);
            Component content = findingPanel.render(list, entityComponent, null, row.getDetail().get(0), isSelected);
            if (findingHolder.getComponentCount() == 0) {
                findingHolder.add(content, BorderLayout.CENTER);
            }
            findingHolder.setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
            return findingHolder;
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

    /** A finding row whose underlying finding is retained for selection-based resolution. */
    static final class FindingRow implements MListItem {

        @Nonnull
        private final OWLEntity entity;

        @Nonnull
        private final List<String> detail;

        private final Object finding;

        FindingRow(@Nonnull Object finding, @Nonnull OWLEntity entity, @Nonnull List<String> detail) {
            this.finding = checkNotNull(finding);
            this.entity = checkNotNull(entity);
            this.detail = ImmutableList.copyOf(detail);
        }

        Object getFinding() {
            return finding;
        }

        @Nonnull
        OWLEntity getEntity() {
            return entity;
        }

        /** The detail lines shown under the entity, one field per line. */
        @Nonnull
        List<String> getDetail() {
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
