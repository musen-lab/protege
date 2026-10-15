package org.protege.editor.owl.ui.declaration;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.protege.editor.owl.model.OWLModelManager;
import org.protege.editor.owl.model.declaration.*;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/** Drives the real toolbar on the EDT without opening native dialogs. */
public class DeclarationResolutionActions_TestCase {

    private OWLOntologyManager ontologies;
    private OWLDataFactory factory;
    private OWLOntology root;
    private OWLOntology owner;
    private OWLClass missing;
    private OWLClass misplaced;
    private OWLModelManager model;
    private final List<List<OWLOntologyChange>> batches = new ArrayList<>();
    private final AtomicInteger refreshes = new AtomicInteger();
    private final Prompts prompts = new Prompts();
    private JList<Object> list;
    private DeclarationResolutionActions actions;

    @Before
    public void setUp() throws Exception {
        ontologies = OWLManager.createOWLOntologyManager();
        factory = ontologies.getOWLDataFactory();
        root = ontologies.createOntology(IRI.create("http://example.org/root"));
        owner = ontologies.createOntology(IRI.create("http://example.org/owner"));
        ontologies.applyChange(new AddImport(root, factory.getOWLImportsDeclaration(
                owner.getOntologyID().getOntologyIRI().get())));
        missing = factory.getOWLClass(IRI.create("http://example.org/owner#Missing"));
        misplaced = factory.getOWLClass(IRI.create("http://example.org/owner#Misplaced"));
        ontologies.addAxiom(root, factory.getOWLSubClassOfAxiom(missing, factory.getOWLThing()));
        ontologies.addAxiom(root, factory.getOWLDeclarationAxiom(misplaced));
        model = mock(OWLModelManager.class);
        when(model.getActiveOntology()).thenReturn(root);
        when(model.getOWLOntologyManager()).thenReturn(ontologies);
        when(model.isMutable(any())).thenReturn(true);
        doAnswer(call -> {
            List<OWLOntologyChange> changes = call.getArgument(0);
            batches.add(new ArrayList<>(changes));
            ontologies.applyChanges(changes);
            return null;
        }).when(model).applyChanges(anyList());
        prompts.target = owner;
        SwingUtilities.invokeAndWait(() -> {
            list = new JList<>(rows());
            actions = new DeclarationResolutionActions(model, root, list, updated -> {
                assertTrue(SwingUtilities.isEventDispatchThread());
                refreshes.incrementAndGet();
                list.setListData(updated.toArray());
            }, prompts);
        });
    }

    @After
    public void dispose() throws Exception {
        SwingUtilities.invokeAndWait(() -> actions.dispose());
    }

    @Test
    public void shouldEnableSelectedOnlyForFindingsAndAllForAnyFindings() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            assertFalse(button("Resolve selected…").isEnabled());
            assertTrue(button("Resolve all…").isEnabled());
            list.setSelectedIndex(0); // section header
            assertFalse(button("Resolve selected…").isEnabled());
            list.setSelectedIndex(1);
            assertTrue(button("Resolve selected…").isEnabled());
        });
    }

    @Test
    public void shouldResolveOnlySelectedMissingThroughModelManagerAndRefresh() throws Exception {
        clickSelected(1);
        assertEquals(1, batches.size());
        assertEquals(1, batches.get(0).size());
        assertTrue(owner.containsAxiom(factory.getOWLDeclarationAxiom(missing)));
        assertTrue(owner.getDeclarationAxioms(misplaced).isEmpty());
        assertEquals(1, refreshes.get());
        assertTrue(prompts.errors.isEmpty());
    }

    @Test
    public void shouldResolveMisplacedWithoutAskingForMissingDestination() throws Exception {
        clickSelected(3);
        assertEquals(0, prompts.choices);
        assertEquals(1, batches.size());
        assertTrue(root.getDeclarationAxioms(misplaced).isEmpty());
        assertFalse(owner.getDeclarationAxioms(misplaced).isEmpty());
    }

    @Test
    public void shouldResolveAllAsOneUndoableBatchAndDisableCleanReport() throws Exception {
        prompts.duringConfirmation = () -> assertTrue(batches.isEmpty());
        click("Resolve all…");
        assertEquals(1, batches.size());
        assertEquals(3, batches.get(0).size());
        assertEquals(2, prompts.preview.getFindingCount());
        assertTrue(DeclarationChecker.forcingAllChecks().check(root).isEmpty());
        SwingUtilities.invokeAndWait(() -> {
            assertFalse(button("Resolve all…").isEnabled());
            assertFalse(button("Resolve selected…").isEnabled());
        });
    }

    @Test
    public void shouldNotApplyAnythingWhenDestinationChoiceIsCancelled() throws Exception {
        prompts.target = null;
        click("Resolve all…");
        assertNull(prompts.preview);
        assertTrue(batches.isEmpty());
        assertEquals(0, refreshes.get());
    }

    @Test
    public void shouldNotApplyAnythingWhenPreviewIsCancelled() throws Exception {
        prompts.approve = false;
        click("Resolve all…");
        assertNotNull(prompts.preview);
        assertTrue(batches.isEmpty());
        assertEquals(0, refreshes.get());
    }

    @Test
    public void shouldRejectStaleReportThenAllowExplicitRefresh() throws Exception {
        ontologies.addAxiom(owner, factory.getOWLDeclarationAxiom(missing));
        clickSelected(1);
        assertTrue(batches.isEmpty());
        assertEquals(1, prompts.errors.size());
        click("Refresh");
        assertEquals(1, refreshes.get());
        assertEquals(4, list.getModel().getSize());
        assertTrue(list.getModel().getElementAt(1) instanceof DeclarationCheckReportPanel.MessageRow);
    }

    @Test
    public void shouldRefuseWhenActiveOntologyChangesDuringConfirmation() throws Exception {
        prompts.duringConfirmation = () -> when(model.getActiveOntology()).thenReturn(owner);
        click("Resolve all…");
        assertTrue(batches.isEmpty());
        assertEquals(1, prompts.errors.size());
    }

    @Test
    public void shouldRefuseWhenSourceBecomesReadOnlyDuringConfirmation() throws Exception {
        prompts.duringConfirmation = () -> when(model.isMutable(root)).thenReturn(false);
        click("Resolve all…");
        assertTrue(batches.isEmpty());
        assertEquals(1, prompts.errors.size());
        assertFalse(root.getDeclarationAxioms(misplaced).isEmpty());
    }

    @Test
    public void shouldRefuseChangedDeclarationAnnotationsAfterPreview() throws Exception {
        prompts.duringConfirmation = () -> {
            ontologies.removeAxiom(root, factory.getOWLDeclarationAxiom(misplaced));
            ontologies.addAxiom(root, factory.getOWLDeclarationAxiom(misplaced, Collections.singleton(
                    factory.getOWLAnnotation(factory.getRDFSLabel(), factory.getOWLLiteral("new annotation")))));
        };
        clickSelected(3);
        assertTrue(batches.isEmpty());
        assertEquals(1, prompts.errors.size());
        assertTrue(owner.getDeclarationAxioms(misplaced).isEmpty());
    }

    @Test
    public void shouldReportApplyFailureWithoutClaimingNoEditsWereMade() throws Exception {
        doThrow(new IllegalStateException("application failed")).when(model).applyChanges(anyList());
        click("Resolve all…");
        assertEquals(1, prompts.errors.size());
        assertTrue(prompts.errors.get(0).contains("Some edits may have been applied"));
    }

    @Test
    public void shouldKeepTheEdtResponsiveWhilePlanning() throws Exception {
        java.util.Set<OWLOntology> closure = root.getImportsClosure();
        OWLOntology slowRoot = spy(root);
        java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        doAnswer(call -> {
            if (!SwingUtilities.isEventDispatchThread()) {
                entered.countDown();
                assertTrue(release.await(5, java.util.concurrent.TimeUnit.SECONDS));
            }
            return closure;
        }).when(slowRoot).getImportsClosure();
        when(model.getActiveOntology()).thenReturn(slowRoot);
        // Mockito spies have identity-based equality; expose that same root in the UI inventory.
        OWLOntologyManager visibleManager = mock(OWLOntologyManager.class);
        when(visibleManager.getOntologies()).thenReturn(Collections.singleton(slowRoot));
        when(model.getOWLOntologyManager()).thenReturn(visibleManager);
        SwingUtilities.invokeAndWait(() -> {
            actions.dispose();
            actions = new DeclarationResolutionActions(model, slowRoot, list,
                    updated -> list.setListData(updated.toArray()), prompts);
            button("Resolve all…").doClick();
        });
        try {
            assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
            SwingUtilities.invokeAndWait(() -> {
                assertTrue(actions.isBusy());
                assertFalse(button("Resolve all…").isEnabled());
            });
        } finally {
            release.countDown();
        }
        awaitIdle();
        assertEquals(1, batches.size());
    }

    @Test
    public void shouldNotApplyAfterTheReportClosesDuringPreview() throws Exception {
        prompts.duringConfirmation = () -> actions.dispose();
        click("Resolve all…");
        assertTrue(batches.isEmpty());
        assertTrue(prompts.errors.isEmpty());
    }

    private Object[] rows() {
        return DeclarationCheckReportPanel.rows(DeclarationReportOutline.of(
                DeclarationChecker.forcingAllChecks().check(root)), root.getImportsClosure()).toArray();
    }

    private void clickSelected(int index) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            list.setSelectedIndex(index);
            button("Resolve selected…").doClick();
        });
        awaitIdle();
    }

    private void click(String name) throws Exception {
        SwingUtilities.invokeAndWait(() -> button(name).doClick());
        awaitIdle();
    }

    private void awaitIdle() throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(15);
        java.util.concurrent.atomic.AtomicBoolean busy = new java.util.concurrent.atomic.AtomicBoolean(true);
        do {
            SwingUtilities.invokeAndWait(() -> busy.set(actions.isBusy()));
            if (!busy.get()) {
                return;
            }
            Thread.sleep(10);
        } while (System.nanoTime() < deadline);
        fail("Background operation did not finish");
    }

    private JButton button(String name) {
        for (java.awt.Component component : actions.getToolbar().getComponents()) {
            if (component instanceof JButton && ((JButton) component).getText().equals(name)) {
                return (JButton) component;
            }
        }
        throw new AssertionError("Missing button: " + name);
    }

    private static final class Prompts implements DeclarationResolutionActions.Interaction {
        OWLOntology target;
        boolean approve = true;
        int choices;
        Runnable duringConfirmation = () -> { };
        DeclarationResolutionPlanner.Plan preview;
        final List<String> errors = new ArrayList<>();

        @Override
        public OWLOntology chooseMissingTarget(List<OWLOntology> ontologies, OWLOntology root) {
            choices++;
            return target;
        }

        @Override
        public boolean confirm(DeclarationResolutionPlanner.Plan plan) {
            preview = plan;
            duringConfirmation.run();
            return approve;
        }

        @Override
        public void showError(String message) {
            errors.add(message);
        }
    }
}
