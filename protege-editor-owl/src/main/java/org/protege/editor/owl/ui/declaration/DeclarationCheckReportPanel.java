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
import org.protege.editor.owl.ui.renderer.OWLCellRenderer;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyID;

import javax.annotation.Nonnull;
import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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

    private final MList list = new MList();

    private DeclarationResolutionActions resolutionActions;

    DeclarationCheckReportPanel(@Nonnull OWLEditorKit owlEditorKit,
                                @Nonnull DeclarationReportOutline outline) {
        this(owlEditorKit, rows(outline,
                owlEditorKit.getOWLModelManager().getActiveOntology().getImportsClosure()));
    }

    private DeclarationCheckReportPanel(OWLEditorKit owlEditorKit, List<Object> reportRows) {
        this(new OWLCellRenderer(checkNotNull(owlEditorKit)), reportRows);
        OWLOntology root = owlEditorKit.getOWLModelManager().getActiveOntology();
        resolutionActions = new DeclarationResolutionActions(
                owlEditorKit.getOWLModelManager(), root, list,
                updated -> list.setListData(updated.toArray()),
                new DeclarationResolutionDialogs(this,
                        DeclarationDestinationPanel.entityRenderer(owlEditorKit)));
        add(resolutionActions.getToolbar(), BorderLayout.SOUTH);
    }

    /**
     * Lays out the report with the given renderer for the entity part of each finding row.
     *
     * @param entityRenderer the renderer that draws a finding's entity
     * @param outline the declaration report outline to present
     * @param ontologies the ontologies in the import closure
     */
    DeclarationCheckReportPanel(@Nonnull ListCellRenderer entityRenderer,
                                @Nonnull DeclarationReportOutline outline,
                                @Nonnull Collection<OWLOntology> ontologies) {
        this(entityRenderer, rows(outline, ontologies));
    }

    private DeclarationCheckReportPanel(ListCellRenderer entityRenderer, List<Object> reportRows) {
        checkNotNull(entityRenderer);
        if (entityRenderer instanceof OWLCellRenderer) {
            ((OWLCellRenderer) entityRenderer).setWrap(false);
        }
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
        showDialog(owlEditorKit, rows(outline,
                owlEditorKit.getOWLModelManager().getActiveOntology().getImportsClosure()));
    }

    /** Shows precomputed rows so the Tools action need not scan large ontologies on the EDT. */
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
     * @param ontologies the ontologies in the import closure
     * @return the ordered list rows, mixing {@link MListSectionHeader} and {@link MListItem}
     * @throws NullPointerException if {@code outline} is {@code null}
     */
    @Nonnull
    static List<Object> rows(@Nonnull DeclarationReportOutline outline,
                             @Nonnull Collection<OWLOntology> ontologies) {
        checkNotNull(outline);
        List<Object> rows = new ArrayList<>();
        rows.add(new SectionHeader(outline.getMissingSection().getHeading()));
        if (outline.getMissing().isEmpty()) {
            rows.add(new MessageRow(NO_MISSING_DECLARATIONS));
        } else {
            Map<OWLEntity, List<OWLOntologyID>> mentions = directMentionsOf(outline.getMissing(), ontologies);
            for (MissingDeclarationFinding finding : outline.getMissing()) {
                rows.add(new FindingRow(finding, finding.getEntity(),
                        missingDetail(mentions.getOrDefault(finding.getEntity(), ImmutableList.of()))));
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

    /**
     * Maps each missing entity to the ontologies whose own axioms or header annotations mention
     * it, scanning each ontology once for the whole report.
     */
    @Nonnull
    private static Map<OWLEntity, List<OWLOntologyID>> directMentionsOf(
            @Nonnull Collection<MissingDeclarationFinding> findings,
            @Nonnull Collection<OWLOntology> ontologies) {
        Set<OWLEntity> missing = findings.stream()
                .map(MissingDeclarationFinding::getEntity)
                .collect(Collectors.toSet());
        Map<OWLEntity, List<OWLOntologyID>> mentions = new HashMap<>();
        for (OWLOntology ontology : ontologies) {
            Set<OWLEntity> mentioned = new HashSet<>();
            for (OWLAxiom axiom : ontology.getAxioms()) {
                addMissing(axiom.getSignature(), missing, mentioned);
            }
            for (OWLAnnotation annotation : ontology.getAnnotations()) {
                addMissing(annotation.getSignature(), missing, mentioned);
            }
            for (OWLEntity entity : mentioned) {
                mentions.computeIfAbsent(entity, key -> new ArrayList<>()).add(ontology.getOntologyID());
            }
        }
        return mentions;
    }

    private static void addMissing(@Nonnull Set<OWLEntity> signature,
                                   @Nonnull Set<OWLEntity> missing,
                                   @Nonnull Set<OWLEntity> mentioned) {
        for (OWLEntity entity : signature) {
            if (missing.contains(entity)) {
                mentioned.add(entity);
            }
        }
    }

    @Nonnull
    private static List<String> missingDetail(@Nonnull List<OWLOntologyID> mentioningOntologies) {
        return ImmutableList.of("Used in: " + OntologyIdFormat.formatAll(mentioningOntologies.stream()
                .sorted(Comparator.comparing(OntologyIdFormat::format))
                .collect(Collectors.toList())));
    }

    @Nonnull
    private static List<String> misplacedDetail(@Nonnull MisplacedDeclarationFinding finding) {
        return ImmutableList.of(DeclarationReportOutline.suggestedPlacementOf(finding));
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

        private final JPanel findingPanel = new JPanel(new BorderLayout(0, 1));

        private final JPanel detailPanel = new JPanel(new GridLayout(0, 1));

        private final JLabel messageLabel = new JLabel();

        private FindingCellRenderer(@Nonnull ListCellRenderer entityRenderer) {
            this.entityRenderer = checkNotNull(entityRenderer);
            detailPanel.setOpaque(false);
            detailPanel.setBorder(BorderFactory.createEmptyBorder(0, 20, 0, 0));
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
            if (entityRenderer instanceof OWLCellRenderer) {
                JViewport viewport = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, list);
                if (viewport != null && viewport.getExtentSize().width > 0) {
                    Dimension size = entityComponent.getPreferredSize();
                    entityComponent.setPreferredSize(new Dimension(
                            Math.max(1, viewport.getExtentSize().width - 40), size.height));
                }
            }
            findingPanel.removeAll();
            findingPanel.setOpaque(true);
            findingPanel.setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
            findingPanel.add(entityComponent, BorderLayout.CENTER);
            detailPanel.removeAll();
            for (String line : row.getDetail()) {
                JLabel detailLabel = new JLabel(line);
                detailLabel.setForeground(isSelected ? list.getSelectionForeground() : Color.DARK_GRAY);
                detailLabel.setFont(detailLabel.getFont().deriveFont(12.0f));
                detailPanel.add(detailLabel);
            }
            findingPanel.add(detailPanel, BorderLayout.SOUTH);
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
