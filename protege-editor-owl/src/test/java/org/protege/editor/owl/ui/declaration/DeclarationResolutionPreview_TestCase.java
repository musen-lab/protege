package org.protege.editor.owl.ui.declaration;

import org.junit.Before;
import org.junit.Test;
import org.protege.editor.owl.model.declaration.*;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;

import javax.swing.*;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import static org.junit.Assert.*;

public class DeclarationResolutionPreview_TestCase {

    private OWLOntologyManager manager;
    private OWLDataFactory factory;
    private OWLOntology root;
    private OWLOntology owner;
    private OWLClass missing;
    private OWLClass misplaced;

    @Before
    public void setUp() throws Exception {
        manager = OWLManager.createOWLOntologyManager();
        factory = manager.getOWLDataFactory();
        root = manager.createOntology(IRI.create("http://example.org/root"));
        owner = manager.createOntology(IRI.create("http://example.org/owner"));
        manager.applyChange(new AddImport(root, factory.getOWLImportsDeclaration(owner.getOntologyID().getOntologyIRI().get())));
        missing = factory.getOWLClass(IRI.create("http://example.org/owner#Missing"));
        misplaced = factory.getOWLClass(IRI.create("http://example.org/owner#Misplaced"));
    }

    @Test
    public void shouldGroupAnnotatedVariantsAndMoveSourcesIntoOneRowPerEntity() {
        DeclarationResolutionPlanner.Plan plan = mixedPlan();
        List<DeclarationResolutionPreview.Row> rows = DeclarationResolutionPreview.rows(plan);
        assertEquals(2, rows.size());
        DeclarationResolutionPreview.Row add = row(rows, missing);
        assertEquals("Add declaration", add.heading());
        assertEquals(owner.getOntologyID(), add.destination);
        assertTrue(add.sources.isEmpty());
        assertEquals(1, add.additions);
        DeclarationResolutionPreview.Row move = row(rows, misplaced);
        assertEquals("Move declaration", move.heading());
        assertEquals(Collections.singleton(root.getOntologyID()), move.sources);
        assertEquals(owner.getOntologyID(), move.destination);
        assertEquals(2, move.additions);
        assertEquals(2, move.removals);
        assertThrows(UnsupportedOperationException.class, () -> plan.getDestinations().clear());
    }

    @Test
    public void shouldShowTheDestinationEvenWhenItsDeclarationAlreadyExists() throws Exception {
        OWLOntology other = manager.createOntology(IRI.create("http://example.org/other"));
        manager.applyChange(new AddImport(root, factory.getOWLImportsDeclaration(other.getOntologyID().getOntologyIRI().get())));
        manager.addAxiom(root, factory.getOWLDeclarationAxiom(misplaced));
        manager.addAxiom(other, factory.getOWLDeclarationAxiom(misplaced));
        DeclarationReport report = DeclarationChecker.forcingAllChecks().check(root);
        DeclarationResolutionPlanner.Plan plan = new DeclarationResolutionPlanner(root, ontology -> true)
                .planForTargets(Collections.emptyList(), report.getMisplaced().getFindings(),
                        Collections.singletonMap(misplaced, root));
        DeclarationResolutionPreview.Row move = DeclarationResolutionPreview.rows(plan).get(0);
        assertEquals("Move declaration", move.heading());
        assertEquals(root.getOntologyID(), move.destination);
        assertEquals(Collections.singleton(other.getOntologyID()), move.sources);
        assertEquals(0, move.additions);
        assertEquals(1, move.removals);
    }

    @Test
    public void shouldShowAnUnchangedDeclarationAsKeepRatherThanAddOrMove() {
        manager.addAxiom(root, factory.getOWLDeclarationAxiom(misplaced));
        DeclarationReport report = DeclarationChecker.forcingAllChecks().check(root);
        DeclarationResolutionPlanner.Plan plan = new DeclarationResolutionPlanner(root, ontology -> true)
                .planForTargets(Collections.emptyList(), report.getMisplaced().getFindings(),
                        Collections.singletonMap(misplaced, root));
        assertEquals("Keep declaration", DeclarationResolutionPreview.rows(plan).get(0).heading());
    }

    @Test
    public void shouldRenderExactlyTwoCompactLinesPerEntity() throws Exception {
        DeclarationResolutionPlanner.Plan plan = mixedPlan();
        Icon icon = new ImageIcon(new java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB));
        SwingUtilities.invokeAndWait(() -> {
            DeclarationResolutionPreview preview = new DeclarationResolutionPreview(plan,
                    (list, value, index, selected, focused) -> new JLabel(((OWLEntity) value).getIRI().getShortForm(), icon, JLabel.LEADING));
            JList list = find(preview.getComponent(), JList.class).get(0);
            Component first = null;
            for (int index = 0; index < list.getModel().getSize(); index++) {
                Object value = list.getModel().getElementAt(index);
                Component rendered = list.getCellRenderer().getListCellRendererComponent(list, value, index, false, false);
                if (first == null) {
                    first = rendered;
                } else {
                    assertSame("One renderer is reused for all rows", first, rendered);
                }
                assertEquals(5, ((JComponent) rendered).getInsets().top);
                assertEquals(6, ((JComponent) rendered).getInsets().bottom); // Includes the separator.
                List<JLabel> labels = find(rendered, JLabel.class);
                assertEquals(1, labels.stream().filter(label -> label.getIcon() == icon).count());
                assertEquals(3, labels.size()); // Action + entity on line one, locations on line two.
                if (((DeclarationResolutionPreview.Row) value).entity.equals(misplaced)) {
                    assertTrue(labels.stream().anyMatch(label -> label.getText().equals(
                            "From: http://example.org/root -> To: http://example.org/owner")));
                    assertTrue(labels.stream().anyMatch(label -> label.getText().equals("Move declaration:") && label.getFont().isBold()));
                } else {
                    assertTrue(labels.stream().anyMatch(label -> label.getText().equals("To: http://example.org/owner")));
                }
            }
            list.setSize(list.getPreferredSize());
            java.awt.Rectangle bounds = list.getCellBounds(0, 0);
            java.awt.event.MouseEvent hover = new java.awt.event.MouseEvent(list, java.awt.event.MouseEvent.MOUSE_MOVED,
                    0, 0, bounds.x + 1, bounds.y + 1, 0, false);
            assertTrue(list.getToolTipText(hover).contains(missing.getIRI().toString()));
            bounds = list.getCellBounds(1, 1);
            hover = new java.awt.event.MouseEvent(list, java.awt.event.MouseEvent.MOUSE_MOVED,
                    0, 0, bounds.x + 1, bounds.y + 1, 0, false);
            assertTrue(list.getToolTipText(hover).contains("axioms: 2 added, 2 removed"));
        });
    }

    @Test
    public void shouldKeepAllMoveSourcesOnOneLocationLine() throws Exception {
        OWLOntology other = manager.createOntology(IRI.create("http://example.org/other"));
        manager.applyChange(new AddImport(root, factory.getOWLImportsDeclaration(other.getOntologyID().getOntologyIRI().get())));
        manager.addAxiom(root, factory.getOWLDeclarationAxiom(misplaced));
        manager.addAxiom(other, factory.getOWLDeclarationAxiom(misplaced));
        DeclarationReport report = DeclarationChecker.forcingAllChecks().check(root);
        DeclarationResolutionPlanner.Plan plan = new DeclarationResolutionPlanner(root, ontology -> true)
                .planForTargets(Collections.emptyList(), report.getMisplaced().getFindings(),
                        Collections.singletonMap(misplaced, owner));
        DeclarationResolutionPreview.Row row = DeclarationResolutionPreview.rows(plan).get(0);
        assertEquals(2, row.sources.size());
        String location = row.location();
        assertTrue(location.contains("http://example.org/root"));
        assertTrue(location.contains("http://example.org/other"));
        assertTrue(location.endsWith(" -> To: http://example.org/owner"));
        assertFalse(location.contains("\n"));
    }

    @Test
    public void shouldKeepPunnedEntitiesInDifferentRows() {
        OWLNamedIndividual individual = factory.getOWLNamedIndividual(missing.getIRI());
        manager.addAxiom(root, factory.getOWLClassAssertionAxiom(missing, individual));
        DeclarationReport report = DeclarationChecker.forcingAllChecks().check(root);
        Map<OWLEntity, OWLOntology> targets = new HashMap<>();
        targets.put(missing, owner);
        targets.put(individual, owner);
        DeclarationResolutionPlanner.Plan plan = new DeclarationResolutionPlanner(root, ontology -> true)
                .planForTargets(report.getMissing().getFindings(), Collections.emptyList(), targets);
        List<DeclarationResolutionPreview.Row> rows = DeclarationResolutionPreview.rows(plan);
        assertEquals(2, rows.size());
        assertNotEquals(rows.get(0).entity.getEntityType(), rows.get(1).entity.getEntityType());
    }

    @Test(timeout = 30_000)
    public void shouldBuildALargePreviewWithoutRetainingThousandsOfSwingComponents() throws Exception {
        Map<OWLEntity, OWLOntology> targets = new HashMap<>();
        for (int i = 0; i < 10_000; i++) {
            OWLClass entity = factory.getOWLClass(IRI.create("http://example.org/owner#C" + i));
            manager.addAxiom(root, i % 2 == 0 ? factory.getOWLSubClassOfAxiom(entity, factory.getOWLThing())
                    : factory.getOWLDeclarationAxiom(entity));
            targets.put(entity, owner);
        }
        DeclarationReport report = DeclarationChecker.forcingAllChecks().check(root);
        DeclarationResolutionPlanner.Plan plan = new DeclarationResolutionPlanner(root, ontology -> true)
                .planForTargets(report.getMissing().getFindings(), report.getMisplaced().getFindings(), targets);
        assertEquals(10_000, DeclarationResolutionPreview.rows(plan).size());
        SwingUtilities.invokeAndWait(() -> {
            DeclarationResolutionPreview preview = new DeclarationResolutionPreview(plan, new DefaultListCellRenderer());
            JList list = find(preview.getComponent(), JList.class).get(0);
            assertEquals(10_000, list.getModel().getSize());
            assertTrue(find(preview.getComponent(), JLabel.class).size() < 10);
        });
    }

    private DeclarationResolutionPlanner.Plan mixedPlan() {
        manager.addAxiom(root, factory.getOWLSubClassOfAxiom(missing, factory.getOWLThing()));
        manager.addAxiom(root, factory.getOWLDeclarationAxiom(misplaced));
        manager.addAxiom(root, factory.getOWLDeclarationAxiom(misplaced, Collections.singleton(
                factory.getOWLAnnotation(factory.getRDFSLabel(), factory.getOWLLiteral("keep annotation")))));
        DeclarationReport report = DeclarationChecker.forcingAllChecks().check(root);
        Map<OWLEntity, OWLOntology> targets = new HashMap<>();
        targets.put(missing, owner);
        targets.put(misplaced, owner);
        return new DeclarationResolutionPlanner(root, ontology -> true)
                .planForTargets(report.getMissing().getFindings(), report.getMisplaced().getFindings(), targets);
    }

    private static DeclarationResolutionPreview.Row row(List<DeclarationResolutionPreview.Row> rows, OWLEntity entity) {
        return rows.stream().filter(row -> row.entity.equals(entity)).findFirst().get();
    }

    private static <T> List<T> find(Component component, Class<T> type) {
        List<T> matches = new ArrayList<>();
        if (type.isInstance(component)) {
            matches.add(type.cast(component));
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                matches.addAll(find(child, type));
            }
        }
        return matches;
    }
}
