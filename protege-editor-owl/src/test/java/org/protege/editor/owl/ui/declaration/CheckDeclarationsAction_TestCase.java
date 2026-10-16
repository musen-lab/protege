package org.protege.editor.owl.ui.declaration;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.protege.editor.owl.model.OWLModelManager;
import org.protege.editor.owl.model.event.EventType;
import org.protege.editor.owl.model.event.OWLModelManagerChangeEvent;
import org.protege.editor.owl.model.event.OWLModelManagerListener;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class CheckDeclarationsAction_TestCase {

    private OWLOntologyManager ontologies;
    private OWLModelManager model;
    private final AtomicReference<OWLOntology> active = new AtomicReference<>();
    private CheckDeclarationsAction action;
    private OWLModelManagerListener listener;
    private OWLOntologyChangeListener contentListener;

    @Before
    public void setUp() throws Exception {
        ontologies = OWLManager.createOWLOntologyManager();
        model = mock(OWLModelManager.class);
        when(model.getActiveOntology()).thenAnswer(call -> active.get());
        when(model.getOntologies()).thenAnswer(call -> ontologies.getOntologies());
        when(model.getOWLOntologyManager()).thenReturn(ontologies);
        action = new CheckDeclarationsAction() {
            @Override
            public OWLModelManager getOWLModelManager() {
                return model;
            }
        };
        SwingUtilities.invokeAndWait(() -> {
            try {
                action.initialise();
            } catch (Exception ex) {
                throw new AssertionError(ex);
            }
        });
        ArgumentCaptor<OWLModelManagerListener> captor = ArgumentCaptor.forClass(OWLModelManagerListener.class);
        verify(model).addListener(captor.capture());
        listener = captor.getValue();
        ArgumentCaptor<OWLOntologyChangeListener> contentCaptor = ArgumentCaptor.forClass(OWLOntologyChangeListener.class);
        verify(model).addOntologyChangeListener(contentCaptor.capture());
        contentListener = contentCaptor.getValue();
    }

    @After
    public void dispose() throws Exception {
        SwingUtilities.invokeAndWait(action::dispose);
    }

    @Test
    public void shouldStartDisabledWithoutALoadedOntology() {
        assertFalse(action.isEnabled());
    }

    @Test
    public void shouldEnableWhenAnOntologyIsLoaded() throws Exception {
        active.set(populatedOntology("urn:loaded"));
        fire(EventType.ONTOLOGY_LOADED);
        assertTrue(action.isEnabled());
    }

    @Test
    public void shouldDisableForANewEmptyAnonymousOntology() throws Exception {
        active.set(ontologies.createOntology());
        fire(EventType.ONTOLOGY_CREATED);
        assertFalse(action.isEnabled());
    }

    @Test
    public void shouldDisableWhenTheActiveOntologyIsNoLongerLoadedAndEnableOnReopen() throws Exception {
        OWLOntology ontology = populatedOntology("urn:loaded");
        active.set(ontology);
        fire(EventType.ACTIVE_ONTOLOGY_CHANGED);
        assertTrue(action.isEnabled());
        ontologies.removeOntology(ontology);
        fire(EventType.ACTIVE_ONTOLOGY_CHANGED);
        assertFalse(action.isEnabled());
        active.set(populatedOntology("urn:reopened"));
        fire(EventType.ONTOLOGY_LOADED);
        assertTrue(action.isEnabled());
    }

    @Test
    public void shouldDisableWhenThereIsNoActiveOntologyEvenIfOthersAreLoaded() throws Exception {
        ontologies.createOntology(IRI.create("urn:other"));
        fire(EventType.ACTIVE_ONTOLOGY_CHANGED);
        assertFalse(action.isEnabled());
    }

    @Test
    public void shouldIgnoreProgrammaticInvocationWithoutAnOntology() throws Exception {
        SwingUtilities.invokeAndWait(() -> action.actionPerformed(new ActionEvent(this, 0, "check")));
        assertFalse(action.isEnabled());
        verify(model, never()).getOWLOntologyManager();
    }

    @Test
    public void shouldRemoveTheListenersAndRemainDisabledAfterDisposal() throws Exception {
        active.set(populatedOntology("urn:disposed"));
        fire(EventType.ONTOLOGY_CREATED);
        assertTrue(action.isEnabled());
        SwingUtilities.invokeAndWait(action::dispose);
        verify(model).removeListener(listener);
        verify(model).removeOntologyChangeListener(contentListener);
        fire(EventType.ONTOLOGY_LOADED);
        assertFalse(action.isEnabled());
    }

    @Test
    public void shouldNotReenableDuringCheckingOrAfterTheLastOntologyDisappears() throws Exception {
        OWLOntology ontology = populatedOntology("urn:loaded");
        Set<OWLOntology> closure = ontology.getImportsClosure();
        OWLOntology slowOntology = spy(ontology);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch completion = new CountDownLatch(1);
        doAnswer(call -> {
            entered.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return closure;
        }).when(slowOntology).getImportsClosure();
        active.set(slowOntology);
        when(model.getOntologies()).thenReturn(Collections.singleton(slowOntology));
        fire(EventType.ONTOLOGY_LOADED);
        SwingUtilities.invokeAndWait(() -> action.actionPerformed(new ActionEvent(this, 0, "check")));
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            fire(EventType.ONTOLOGY_LOADED);
            assertFalse(action.isEnabled());
            active.set(null);
            when(model.getOntologies()).thenReturn(Collections.emptySet());
            when(model.getActiveOntology()).thenAnswer(call -> {
                completion.countDown();
                return active.get();
            });
            fire(EventType.ACTIVE_ONTOLOGY_CHANGED);
        } finally {
            release.countDown();
        }
        assertTrue(completion.await(5, TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(() -> assertFalse(action.isEnabled()));
    }

    @Test
    public void shouldEnableAfterTheFirstAxiomAndDisableAfterTheLastAxiomIsRemoved() throws Exception {
        OWLOntology ontology = ontologies.createOntology();
        active.set(ontology);
        fire(EventType.ONTOLOGY_CREATED);
        assertFalse(action.isEnabled());
        OWLAxiom declaration = ontologies.getOWLDataFactory().getOWLDeclarationAxiom(
                ontologies.getOWLDataFactory().getOWLClass(IRI.create("urn:entity")));
        fireContent(new AddAxiom(ontology, declaration));
        assertTrue(action.isEnabled());
        fireContent(new RemoveAxiom(ontology, declaration));
        assertFalse(action.isEnabled());
    }

    @Test
    public void shouldSwitchAvailabilityBetweenPopulatedAndEmptyOntologies() throws Exception {
        OWLOntology populated = populatedOntology("urn:populated");
        OWLOntology empty = ontologies.createOntology();
        active.set(populated);
        fire(EventType.ONTOLOGY_LOADED);
        assertTrue(action.isEnabled());
        active.set(empty);
        fire(EventType.ACTIVE_ONTOLOGY_CHANGED);
        assertFalse(action.isEnabled());
        active.set(populated);
        fire(EventType.ACTIVE_ONTOLOGY_CHANGED);
        assertTrue(action.isEnabled());
    }

    @Test
    public void shouldIgnoreProgrammaticInvocationForAnEmptyOntology() throws Exception {
        active.set(ontologies.createOntology());
        fire(EventType.ONTOLOGY_CREATED);
        SwingUtilities.invokeAndWait(() -> action.actionPerformed(new ActionEvent(this, 0, "check")));
        assertFalse(action.isEnabled());
        verify(model, never()).getOWLOntologyManager();
    }

    @Test
    public void shouldUpdateSwingAvailabilityOnTheEdtAfterBackgroundContentChanges() throws Exception {
        OWLOntology ontology = ontologies.createOntology();
        active.set(ontology);
        fire(EventType.ONTOLOGY_CREATED);
        java.util.concurrent.atomic.AtomicBoolean updatedOnEdt = new java.util.concurrent.atomic.AtomicBoolean();
        action.addPropertyChangeListener(event -> {
            if ("enabled".equals(event.getPropertyName()) && Boolean.TRUE.equals(event.getNewValue())) {
                updatedOnEdt.set(SwingUtilities.isEventDispatchThread());
            }
        });
        AddAxiom change = new AddAxiom(ontology, ontologies.getOWLDataFactory().getOWLDeclarationAxiom(
                ontologies.getOWLDataFactory().getOWLClass(IRI.create("urn:entity"))));
        ontologies.applyChange(change);
        contentListener.ontologiesChanged(Collections.singletonList(change));
        SwingUtilities.invokeAndWait(() -> {
            assertTrue(action.isEnabled());
            assertTrue(updatedOnEdt.get());
        });
    }

    private OWLOntology populatedOntology(String iri) throws Exception {
        OWLOntology ontology = ontologies.createOntology(IRI.create(iri));
        ontologies.addAxiom(ontology, ontologies.getOWLDataFactory().getOWLDeclarationAxiom(
                ontologies.getOWLDataFactory().getOWLClass(IRI.create(iri + "#Entity"))));
        return ontology;
    }

    private void fireContent(OWLOntologyChange change) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ontologies.applyChange(change);
            try {
                contentListener.ontologiesChanged(Collections.singletonList(change));
            } catch (OWLException ex) {
                throw new AssertionError(ex);
            }
        });
    }

    private void fire(EventType type) throws Exception {
        SwingUtilities.invokeAndWait(() -> listener.handleChange(new OWLModelManagerChangeEvent(model, type)));
    }
}
