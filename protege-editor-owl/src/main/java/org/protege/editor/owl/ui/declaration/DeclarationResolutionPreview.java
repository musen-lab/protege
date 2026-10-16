package org.protege.editor.owl.ui.declaration;

import org.protege.editor.owl.model.declaration.DeclarationResolutionPlanner;
import org.protege.editor.owl.model.declaration.OntologyIdFormat;
import org.semanticweb.owlapi.model.OWLDeclarationAxiom;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLOntologyChange;
import org.semanticweb.owlapi.model.OWLOntologyID;

import javax.swing.*;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Presents one spaced, renderer-backed list row per entity rather than a dense axiom dump. */
final class DeclarationResolutionPreview {

    private final JScrollPane component;

    DeclarationResolutionPreview(DeclarationResolutionPlanner.Plan plan, ListCellRenderer entityRenderer) {
        JList<Row> list = new JList<Row>(rows(plan).toArray(new Row[0])) {
            @Override
            public String getToolTipText(MouseEvent event) {
                int index = locationToIndex(event.getPoint());
                if (index < 0 || !getCellBounds(index, index).contains(event.getPoint())) {
                    return null;
                }
                Row row = getModel().getElementAt(index);
                String entity = row.entity.getEntityType().getName() + ": " + row.entity.getIRI();
                return row.additions > 1 || row.removals > 1
                        ? entity + " (axioms: " + row.additions + " added, " + row.removals + " removed)" : entity;
            }
        };
        list.setToolTipText("");
        list.setCellRenderer(new RowRenderer(entityRenderer));
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.getAccessibleContext().setAccessibleName("Declaration changes grouped by entity");
        component = new JScrollPane(list);
        component.setPreferredSize(new Dimension(650, 320));
    }

    JComponent getComponent() {
        return component;
    }

    /** One linear pass groups actual axiom changes without scanning any ontology. */
    static List<Row> rows(DeclarationResolutionPlanner.Plan plan) {
        Map<OWLEntity, Row> rows = new LinkedHashMap<>();
        plan.getDestinations().forEach((entity, target) ->
                rows.put(entity, new Row(entity, target.getOntologyID())));
        for (OWLOntologyChange change : plan.getChanges()) {
            OWLDeclarationAxiom axiom = (OWLDeclarationAxiom) change.getAxiom();
            Row row = rows.get(axiom.getEntity());
            if (change.isAddAxiom()) {
                row.additions++;
            } else {
                row.removals++;
                row.sources.add(change.getOntology().getOntologyID());
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(rows.values()));
    }

    static final class Row {
        final OWLEntity entity;
        final OWLOntologyID destination;
        final Set<OWLOntologyID> sources = new LinkedHashSet<>();
        int additions;
        int removals;

        private Row(OWLEntity entity, OWLOntologyID destination) {
            this.entity = entity;
            this.destination = destination;
        }

        String heading() {
            return removals > 0 ? "Move declaration" : additions > 0 ? "Add declaration" : "Keep declaration";
        }

        String location() {
            String to = "To: " + OntologyIdFormat.format(destination);
            return sources.isEmpty() ? to : "From: " + OntologyIdFormat.formatAll(sources) + " -> " + to;
        }
    }

    /** Reuses one cell renderer; large previews do not retain a Swing component per finding. */
    private static final class RowRenderer implements ListCellRenderer<Row> {
        private final ListCellRenderer entityRenderer;
        private final DeclarationRowPanel rowPanel = new DeclarationRowPanel(true);

        private RowRenderer(ListCellRenderer entityRenderer) {
            this.entityRenderer = entityRenderer;
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Row> list, Row row, int index,
                                                        boolean selected, boolean focused) {
            Component entity = entityRenderer.getListCellRendererComponent(list, row.entity, index, selected, focused);
            return rowPanel.render(list, entity, row.heading() + ":", row.location(), selected);
        }
    }
}
