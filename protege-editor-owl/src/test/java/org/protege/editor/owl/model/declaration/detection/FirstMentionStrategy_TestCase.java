package org.protege.editor.owl.model.declaration.detection;

import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class FirstMentionStrategy_TestCase {

    @Test(timeout = 30_000)
    public void shouldIndexTheClosureOnceForTenThousandSuggestions() throws Exception {
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLDataFactory factory = manager.getOWLDataFactory();
        OWLOntology root = manager.createOntology(IRI.create("http://example.org/root"));
        List<OWLOntology> modules = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            IRI iri = IRI.create("http://example.org/module" + i);
            modules.add(manager.createOntology(iri));
            manager.applyChange(new AddImport(root, factory.getOWLImportsDeclaration(iri)));
        }
        List<OWLClass> missing = new ArrayList<>();
        for (int i = 0; i < 100_000; i++) {
            OWLClass entity = factory.getOWLClass(IRI.create("http://example.org/vocab#C" + i));
            OWLOntology module = modules.get(i % modules.size());
            if (i < 10_000) {
                manager.addAxiom(module, factory.getOWLSubClassOfAxiom(entity, factory.getOWLThing()));
                missing.add(entity);
            } else {
                manager.addAxiom(module, factory.getOWLDeclarationAxiom(entity));
            }
        }
        OWLOntology indexedRoot = spy(root);
        long start = System.nanoTime();
        FirstMentionStrategy strategy = FirstMentionStrategy.over(indexedRoot);
        for (int i = 0; i < missing.size(); i++) {
            assertEquals(Optional.of(modules.get(i % modules.size()).getOntologyID()), strategy.resolve(missing.get(i)));
        }
        verify(indexedRoot, times(1)).getImportsClosure();
        System.out.printf("First-mention suggestions: 100,000 entities, 20 imports, 10,000 suggestions in %.3fs%n",
                (System.nanoTime() - start) / 1e9);
    }

    @Test
    public void shouldKeepPunnedEntitySuggestionsIndependent() throws Exception {
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLDataFactory factory = manager.getOWLDataFactory();
        OWLOntology root = manager.createOntology(IRI.create("http://example.org/root"));
        OWLOntology module = manager.createOntology(IRI.create("http://example.org/module"));
        manager.applyChange(new AddImport(root, factory.getOWLImportsDeclaration(
                module.getOntologyID().getOntologyIRI().get())));
        IRI iri = IRI.create("http://example.org/Punned");
        OWLClass clazz = factory.getOWLClass(iri);
        OWLNamedIndividual individual = factory.getOWLNamedIndividual(iri);
        manager.addAxiom(root, factory.getOWLSubClassOfAxiom(clazz, factory.getOWLThing()));
        manager.addAxiom(module, factory.getOWLSameIndividualAxiom(individual, individual));
        FirstMentionStrategy strategy = FirstMentionStrategy.over(root);
        assertEquals(Optional.of(root.getOntologyID()), strategy.resolve(clazz));
        assertEquals(Optional.of(module.getOntologyID()), strategy.resolve(individual));
        assertEquals(Optional.empty(), strategy.resolve(factory.getOWLClass(IRI.create("urn:unused"))));
    }
}
