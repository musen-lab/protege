package org.protege.editor.owl.model.declaration;

import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.Imports;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/**
 * Verifies how declaration axioms are located across an ontology's import closure.
 *
 * <p>{@code getDeclarationAxioms(entity)} searches one ontology, while
 * {@code isDeclared(entity, Imports.INCLUDED)} answers only whether a declaration exists anywhere
 * in the closure. These tests establish that finding every declaring ontology requires an explicit
 * closure walk, including for duplicate declarations, annotated axioms, anonymous ontologies, and
 * punned entities.
 */
public class DeclarationLocation_TestCase {

    /**
     * Finds every ontology in an import closure that declares an entity.
     *
     * @param root the root of the import closure to search
     * @param e the entity whose declarations are required
     * @return the ontologies in the closure that contain a declaration axiom for {@code e}
     */
    private static Set<OWLOntology> declaringOntologies(OWLOntology root, OWLEntity e) {
        Set<OWLOntology> found = new LinkedHashSet<>();
        for (OWLOntology o : root.getImportsClosure()) {
            if (!o.getDeclarationAxioms(e).isEmpty()) {
                found.add(o);
            }
        }
        return found;
    }

    private static Set<String> ontologyIris(Set<OWLOntology> ontologies) {
        return ontologies.stream()
                .map(o -> o.getOntologyID().getOntologyIRI().get().toString())
                .collect(Collectors.toSet());
    }

    @Test
    public void shouldLookInOneOntologyOnlyWithNoImportsArgument() throws Exception {
        OWLOntology leaf = ClosureFixtures.threeLevelClosure();
        OWLDataFactory df = leaf.getOWLOntologyManager().getOWLDataFactory();
        OWLClass a = df.getOWLClass(IRI.create(ClosureFixtures.BASE + "#A"));

        // A is declared in base, which leaf imports transitively.
        assertTrue("no closure-wide overload exists; the local call must miss it",
                leaf.getDeclarationAxioms(a).isEmpty());
        assertTrue("but the boolean shortcut does see the closure",
                leaf.isDeclared(a, Imports.INCLUDED));

        // The hand-written walk recovers what the local call cannot.
        assertEquals(setOf(ClosureFixtures.BASE), ontologyIris(declaringOntologies(leaf, a)));
    }

    @Test
    public void shouldFindDeclarationsAtEveryDepthOfTheClosure() throws Exception {
        OWLOntology leaf = ClosureFixtures.threeLevelClosure();
        OWLDataFactory df = leaf.getOWLOntologyManager().getOWLDataFactory();

        assertEquals(setOf(ClosureFixtures.LEAF), ontologyIris(declaringOntologies(
                leaf, df.getOWLClass(IRI.create(ClosureFixtures.LEAF + "#C")))));
        assertEquals(setOf(ClosureFixtures.MID), ontologyIris(declaringOntologies(
                leaf, df.getOWLClass(IRI.create(ClosureFixtures.MID + "#B")))));
        assertEquals(setOf(ClosureFixtures.BASE), ontologyIris(declaringOntologies(
                leaf, df.getOWLObjectProperty(IRI.create(ClosureFixtures.BASE + "#p")))));
    }

    @Test
    public void shouldResolveAnUndeclaredEntityToTheEmptySet() throws Exception {
        OWLOntology leaf = ClosureFixtures.threeLevelClosure();
        OWLDataFactory df = leaf.getOWLOntologyManager().getOWLDataFactory();
        OWLClass ghost = df.getOWLClass(IRI.create(ClosureFixtures.MID + "#Ghost"));

        // This empty set is exactly the missing-declaration finding.
        assertTrue(declaringOntologies(leaf, ghost).isEmpty());
        assertFalse(leaf.isDeclared(ghost, Imports.INCLUDED));
    }

    @Test
    public void shouldReportADuplicateDeclarationInEveryOntologyThatCarriesIt() throws Exception {
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

        assertEquals(setOf("http://example.org/dup1", "http://example.org/dup2"),
                ontologyIris(declaringOntologies(two, shared)));
    }

    @Test
    public void shouldKeepTheAnnotationsADeclarationAxiomCarries() throws Exception {
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = m.getOWLDataFactory();
        OWLOntology o = m.createOntology(IRI.create("http://example.org/annotated"));
        OWLClass a = df.getOWLClass(IRI.create("http://example.org/annotated#A"));
        OWLAnnotation note = df.getOWLAnnotation(df.getRDFSComment(), df.getOWLLiteral("why"));
        m.addAxiom(o, df.getOWLDeclarationAxiom(a, java.util.Collections.singleton(note)));

        Set<OWLDeclarationAxiom> decls = o.getDeclarationAxioms(a);
        assertEquals(1, decls.size());
        // An annotated declaration is not interchangeable with a bare one, so a
        // relocation fix has to move the axiom rather than recreate it.
        assertFalse(decls.iterator().next().getAnnotations().isEmpty());
    }

    @Test
    public void shouldGiveNoOntologyIriForAnAnonymousOntology() throws Exception {
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = m.getOWLDataFactory();
        OWLOntology anon = m.createOntology();
        OWLClass a = df.getOWLClass(IRI.create("http://example.org/anon#A"));
        m.addAxiom(anon, df.getOWLDeclarationAxiom(a));

        assertTrue(anon.getOntologyID().isAnonymous());
        assertFalse(anon.getOntologyID().getOntologyIRI().isPresent());
        // An anonymous ontology has no IRI, so any code that reads one must cope
        // with it being absent.
        assertEquals(1, declaringOntologies(anon, a).size());
    }

    @Test
    public void shouldDeclarePunnedEntitiesIndependently() throws Exception {
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = m.getOWLDataFactory();
        OWLOntology o = m.createOntology(IRI.create("http://example.org/pundecl"));
        IRI shared = IRI.create("http://example.org/pundecl#Thing");
        OWLClass asClass = df.getOWLClass(shared);
        OWLNamedIndividual asIndividual = df.getOWLNamedIndividual(shared);
        m.addAxiom(o, df.getOWLDeclarationAxiom(asClass));
        m.addAxiom(o, df.getOWLSubClassOfAxiom(asClass,
                df.getOWLClass(IRI.create("http://example.org/pundecl#Super"))));
        m.addAxiom(o, df.getOWLClassAssertionAxiom(asClass, asIndividual));

        // The class is declared; the individual sharing its IRI is not. The check
        // must key on the entity, never on the IRI alone.
        assertTrue(o.isDeclared(asClass, Imports.INCLUDED));
        assertFalse(o.isDeclared(asIndividual, Imports.INCLUDED));
    }

    @Test
    public void shouldAgreeWithIsDeclaredAcrossTheWholeSignature() throws Exception {
        OWLOntology leaf = ClosureFixtures.threeLevelClosure();

        Set<OWLEntity> undeclaredByWalk = new HashSet<>();
        Set<OWLEntity> undeclaredByApi = new HashSet<>();
        for (OWLEntity e : leaf.getSignature(Imports.INCLUDED)) {
            if (e.isBuiltIn()) continue;
            if (declaringOntologies(leaf, e).isEmpty()) undeclaredByWalk.add(e);
            if (!leaf.isDeclared(e, Imports.INCLUDED)) undeclaredByApi.add(e);
        }

        assertEquals(undeclaredByApi, undeclaredByWalk);
        assertEquals(1, undeclaredByWalk.size());
        assertEquals(ClosureFixtures.MID + "#Ghost",
                undeclaredByWalk.iterator().next().getIRI().toString());
    }

    private static Set<String> setOf(String... values) {
        return new HashSet<>(java.util.Arrays.asList(values));
    }
}
