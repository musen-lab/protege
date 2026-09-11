package org.protege.editor.owl.model.declaration;

import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.Imports;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Verifies the declaration index built from an ontology and its import closure.
 *
 * <p>The index must contain the closure signature, agree with the OWL API's declaration status,
 * and record every declaring and using ontology for each entity. The tests also cover duplicate
 * declarations, anonymous ontologies, cyclic imports, and punned IRIs.
 */
public class DeclarationIndex_TestCase {

    @Test
    public void shouldIncludeEveryEntityInTheImportClosure() throws Exception {
        OWLOntology leaf = ClosureFixtures.threeLevelClosure();

        DeclarationIndex index = DeclarationIndex.over(leaf);

        assertEquals(leaf.getSignature(Imports.INCLUDED), index.getEntities());
    }

    @Test
    public void shouldAgreeWithTheOwlApiDeclarationStatusForEveryEntity() throws Exception {
        OWLOntology leaf = ClosureFixtures.threeLevelClosure();
        DeclarationIndex index = DeclarationIndex.over(leaf);

        for (OWLEntity entity : leaf.getSignature(Imports.INCLUDED)) {
            assertEquals(entity.toString(),
                    leaf.isDeclared(entity, Imports.INCLUDED), index.isDeclared(entity));
        }
    }

    @Test
    public void shouldRecordEveryOntologyThatDeclaresAnEntity() throws Exception {
        OWLOntology leaf = ClosureFixtures.threeLevelClosure();
        OWLDataFactory df = leaf.getOWLOntologyManager().getOWLDataFactory();
        DeclarationIndex index = DeclarationIndex.over(leaf);

        OWLClass a = df.getOWLClass(IRI.create(ClosureFixtures.BASE + "#A"));
        assertEquals(iris(index.getDeclaringOntologies(a)), setOf(ClosureFixtures.BASE));

        OWLClass c = df.getOWLClass(IRI.create(ClosureFixtures.LEAF + "#C"));
        assertEquals(iris(index.getDeclaringOntologies(c)), setOf(ClosureFixtures.LEAF));
    }

    @Test
    public void shouldRecordNoDeclaringOntologyForAnUndeclaredEntity() throws Exception {
        OWLOntology leaf = ClosureFixtures.threeLevelClosure();
        OWLDataFactory df = leaf.getOWLOntologyManager().getOWLDataFactory();
        DeclarationIndex index = DeclarationIndex.over(leaf);

        OWLClass ghost = df.getOWLClass(IRI.create(ClosureFixtures.MID + "#Ghost"));

        assertFalse(index.isDeclared(ghost));
        assertTrue(index.getDeclaringOntologies(ghost).isEmpty());
        assertEquals(iris(index.getUsingOntologies(ghost)), setOf(ClosureFixtures.MID));
    }

    @Test
    public void shouldRecordADuplicateDeclarationInEachOntology() throws Exception {
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = m.getOWLDataFactory();
        IRI oneIri = IRI.create("http://example.org/dup1");
        IRI twoIri = IRI.create("http://example.org/dup2");
        OWLOntology one = m.createOntology(oneIri);
        OWLOntology two = m.createOntology(twoIri);
        m.applyChange(new AddImport(two, df.getOWLImportsDeclaration(oneIri)));
        OWLClass shared = df.getOWLClass(IRI.create("http://example.org/dup1#Shared"));
        m.addAxiom(one, df.getOWLDeclarationAxiom(shared));
        m.addAxiom(two, df.getOWLDeclarationAxiom(shared));

        DeclarationIndex index = DeclarationIndex.over(two);

        assertEquals(setOf("http://example.org/dup1", "http://example.org/dup2"),
                iris(index.getDeclaringOntologies(shared)));
    }

    @Test
    public void shouldHandleAnAnonymousOntology() throws Exception {
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = m.getOWLDataFactory();
        OWLOntology anonymous = m.createOntology();
        OWLClass a = df.getOWLClass(IRI.create("http://example.org/anon#A"));
        OWLClass undeclared = df.getOWLClass(IRI.create("http://example.org/anon#B"));
        m.addAxiom(anonymous, df.getOWLDeclarationAxiom(a));
        m.addAxiom(anonymous, df.getOWLSubClassOfAxiom(a, undeclared));

        assertTrue(anonymous.getOntologyID().isAnonymous());

        DeclarationIndex index = DeclarationIndex.over(anonymous);

        assertTrue(index.isDeclared(a));
        assertFalse(index.isDeclared(undeclared));
        assertEquals(1, index.getUsingOntologies(undeclared).size());
    }

    @Test
    public void shouldTerminateWhenTheImportClosureContainsACycle() throws Exception {
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = m.getOWLDataFactory();
        IRI aIri = IRI.create("http://example.org/cycleA");
        IRI bIri = IRI.create("http://example.org/cycleB");
        OWLOntology a = m.createOntology(aIri);
        OWLOntology b = m.createOntology(bIri);
        m.applyChange(new AddImport(a, df.getOWLImportsDeclaration(bIri)));
        m.applyChange(new AddImport(b, df.getOWLImportsDeclaration(aIri)));
        OWLClass inA = df.getOWLClass(IRI.create(aIri + "#A"));
        OWLClass inB = df.getOWLClass(IRI.create(bIri + "#B"));
        m.addAxiom(a, df.getOWLDeclarationAxiom(inA));
        m.addAxiom(b, df.getOWLDeclarationAxiom(inB));

        DeclarationIndex index = DeclarationIndex.over(a);

        assertEquals(2, index.getEntities().size());
        assertEquals(1, index.getDeclaringOntologies(inA).size());
        assertEquals(1, index.getDeclaringOntologies(inB).size());
    }

    @Test
    public void shouldTrackPunnedEntitiesIndependently() throws Exception {
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = m.getOWLDataFactory();
        OWLOntology o = m.createOntology(IRI.create("http://example.org/pun"));
        IRI shared = IRI.create("http://example.org/pun#Thing");
        OWLClass asClass = df.getOWLClass(shared);
        OWLNamedIndividual asIndividual = df.getOWLNamedIndividual(shared);
        m.addAxiom(o, df.getOWLDeclarationAxiom(asClass));
        m.addAxiom(o, df.getOWLClassAssertionAxiom(asClass, asIndividual));

        DeclarationIndex index = DeclarationIndex.over(o);

        assertTrue(index.isDeclared(asClass));
        assertFalse(index.isDeclared(asIndividual));
    }

    private static Set<String> iris(Set<OWLOntology> ontologies) {
        Set<String> names = new HashSet<>();
        for (OWLOntology o : ontologies) {
            names.add(o.getOntologyID().getOntologyIRI().get().toString());
        }
        return names;
    }

    private static Set<String> setOf(String... values) {
        return new HashSet<>(java.util.Arrays.asList(values));
    }
}
