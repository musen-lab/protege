package org.protege.editor.owl.model.declaration;

import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.Imports;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/**
 * Verifies the OWL API 4.5.29 signature behaviour used to find declaration candidates.
 *
 * <p>The tests cover transitive imports, referenced but undeclared entities, built-in entities,
 * punned IRIs, ontology annotations, import cycles, and the entity-type-specific signature
 * accessors. Together they establish which named entities
 * {@code getSignature(Imports.INCLUDED)} exposes and which annotation values remain outside the
 * signature.
 */
public class ClosureSignatureWalk_TestCase {

    private static Set<String> iris(Set<? extends OWLEntity> entities) {
        return entities.stream().map(e -> e.getIRI().toString()).collect(Collectors.toSet());
    }

    @Test
    public void shouldReachEveryOntologyInTheClosure() throws Exception {
        OWLOntology leaf = ClosureFixtures.threeLevelClosure();

        Set<String> local = iris(leaf.getSignature(Imports.EXCLUDED));
        Set<String> closure = iris(leaf.getSignature(Imports.INCLUDED));

        assertTrue(local.contains(ClosureFixtures.LEAF + "#C"));
        assertFalse("local signature must not reach into imports",
                local.contains(ClosureFixtures.BASE + "#A"));

        assertTrue(closure.contains(ClosureFixtures.LEAF + "#C"));
        assertTrue(closure.contains(ClosureFixtures.MID + "#B"));
        assertTrue(closure.contains(ClosureFixtures.BASE + "#A"));
        assertTrue(closure.contains(ClosureFixtures.BASE + "#p"));
    }

    @Test
    public void shouldIncludeEntitiesThatAreReferencedButNeverDeclared() throws Exception {
        OWLOntology leaf = ClosureFixtures.threeLevelClosure();
        OWLDataFactory df = leaf.getOWLOntologyManager().getOWLDataFactory();
        OWLClass ghost = df.getOWLClass(IRI.create(ClosureFixtures.MID + "#Ghost"));

        // This is the property the missing-declaration check depends on.
        assertTrue("a referenced-only entity must still appear in the signature",
                iris(leaf.getSignature(Imports.INCLUDED)).contains(ghost.getIRI().toString()));
        assertFalse(leaf.isDeclared(ghost, Imports.INCLUDED));
    }

    @Test
    public void shouldCarryBuiltInsThatTheCheckMustFilterOut() throws Exception {
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = m.getOWLDataFactory();
        OWLOntology o = m.createOntology(IRI.create("http://example.org/builtins"));
        OWLClass a = df.getOWLClass(IRI.create("http://example.org/builtins#A"));
        m.addAxiom(o, df.getOWLSubClassOfAxiom(a, df.getOWLThing()));
        m.addAxiom(o, df.getOWLDataPropertyAssertionAxiom(
                df.getOWLDataProperty(IRI.create("http://example.org/builtins#age")),
                df.getOWLNamedIndividual(IRI.create("http://example.org/builtins#i")),
                df.getOWLLiteral(42)));

        Set<OWLEntity> sig = o.getSignature(Imports.INCLUDED);
        Set<OWLEntity> builtIn = sig.stream().filter(OWLEntity::isBuiltIn).collect(Collectors.toSet());

        assertFalse("builtins leak into the signature", builtIn.isEmpty());
        assertTrue(iris(builtIn).contains("http://www.w3.org/2002/07/owl#Thing"));
        assertTrue(iris(builtIn).contains("http://www.w3.org/2001/XMLSchema#integer"));
        // isBuiltIn() is a clean filter: nothing user-defined is caught by it.
        assertFalse(iris(builtIn).contains("http://example.org/builtins#A"));
        assertFalse(iris(builtIn).contains("http://example.org/builtins#age"));
    }

    @Test
    public void shouldYieldOneEntityPerTypeForAPunnedIri() throws Exception {
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = m.getOWLDataFactory();
        OWLOntology o = m.createOntology(IRI.create("http://example.org/pun"));
        IRI shared = IRI.create("http://example.org/pun#Thing");
        m.addAxiom(o, df.getOWLDeclarationAxiom(df.getOWLClass(shared)));
        m.addAxiom(o, df.getOWLDeclarationAxiom(df.getOWLNamedIndividual(shared)));

        Set<OWLEntity> sameIri = o.getEntitiesInSignature(shared, Imports.INCLUDED);

        assertEquals("punning must produce two distinct entities", 2, sameIri.size());
        Set<EntityType<?>> types = sameIri.stream()
                .map(OWLEntity::getEntityType).collect(Collectors.toSet());
        assertTrue(types.contains(EntityType.CLASS));
        assertTrue(types.contains(EntityType.NAMED_INDIVIDUAL));
        assertTrue(o.getPunnedIRIs(Imports.INCLUDED).contains(shared));
    }

    @Test
    public void shouldIncludeAnAnnotationPropertyUsedOnlyAtOntologyLevel() throws Exception {
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = m.getOWLDataFactory();
        OWLOntology o = m.createOntology(IRI.create("http://example.org/ann"));
        OWLAnnotationProperty ap =
                df.getOWLAnnotationProperty(IRI.create("http://example.org/ann#note"));
        m.applyChange(new AddOntologyAnnotation(o, df.getOWLAnnotation(ap, df.getOWLLiteral("x"))));

        // getSignature() folds ontology-level annotations in, so no separate sweep
        // is needed for the property. It is undeclared, so the check will flag it.
        assertTrue(o.getSignature(Imports.INCLUDED).contains(ap));
        assertFalse(o.isDeclared(ap, Imports.INCLUDED));
    }

    @Test
    public void shouldNotContributeAnEntityForAnIriValuedOntologyAnnotation() throws Exception {
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = m.getOWLDataFactory();
        OWLOntology o = m.createOntology(IRI.create("http://example.org/anniri"));
        m.applyChange(new AddOntologyAnnotation(o, df.getOWLAnnotation(
                df.getRDFSSeeAlso(), IRI.create("http://example.org/anniri#Target"))));

        // The property arrives; the IRI-valued target does not, because an IRI in
        // value position has no entity type to give it.
        Set<String> sig = iris(o.getSignature(Imports.INCLUDED));
        assertTrue(sig.contains("http://www.w3.org/2000/01/rdf-schema#seeAlso"));
        assertFalse(sig.contains("http://example.org/anniri#Target"));
    }

    @Test
    public void shouldNotIncludeAnnotationValuesThatAreIris() throws Exception {
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = m.getOWLDataFactory();
        OWLOntology o = m.createOntology(IRI.create("http://example.org/annval"));
        OWLClass subject = df.getOWLClass(IRI.create("http://example.org/annval#A"));
        IRI target = IRI.create("http://example.org/annval#B");
        m.addAxiom(o, df.getOWLDeclarationAxiom(subject));
        m.addAxiom(o, df.getOWLAnnotationAssertionAxiom(
                df.getRDFSSeeAlso(), subject.getIRI(), target));

        Set<String> sig = iris(o.getSignature(Imports.INCLUDED));
        assertTrue(sig.contains("http://example.org/annval#A"));
        // Second documented blind spot: an IRI used only as an annotation value
        // is not an entity reference as far as the signature is concerned.
        assertFalse(sig.contains("http://example.org/annval#B"));
    }

    @Test
    public void shouldTerminateOnAnImportCycle() throws Exception {
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = m.getOWLDataFactory();
        IRI aIri = IRI.create("http://example.org/cycleA");
        IRI bIri = IRI.create("http://example.org/cycleB");
        OWLOntology a = m.createOntology(aIri);
        OWLOntology b = m.createOntology(bIri);
        m.applyChange(new AddImport(a, df.getOWLImportsDeclaration(bIri)));
        m.applyChange(new AddImport(b, df.getOWLImportsDeclaration(aIri)));
        m.addAxiom(a, df.getOWLDeclarationAxiom(df.getOWLClass(IRI.create(aIri + "#A"))));
        m.addAxiom(b, df.getOWLDeclarationAxiom(df.getOWLClass(IRI.create(bIri + "#B"))));

        Set<OWLOntology> closure = a.getImportsClosure();

        assertEquals(2, closure.size());
        assertEquals(2, iris(a.getSignature(Imports.INCLUDED)).size());
    }

    @Test
    public void shouldPartitionTheSignatureAcrossPerEntityTypeAccessors() throws Exception {
        OWLOntology leaf = ClosureFixtures.threeLevelClosure();

        Set<OWLEntity> union = new HashSet<>();
        union.addAll(leaf.getClassesInSignature(Imports.INCLUDED));
        union.addAll(leaf.getObjectPropertiesInSignature(Imports.INCLUDED));
        union.addAll(leaf.getDataPropertiesInSignature(Imports.INCLUDED));
        union.addAll(leaf.getAnnotationPropertiesInSignature(Imports.INCLUDED));
        union.addAll(leaf.getIndividualsInSignature(Imports.INCLUDED));
        union.addAll(leaf.getDatatypesInSignature(Imports.INCLUDED));

        assertEquals(leaf.getSignature(Imports.INCLUDED), union);
    }
}
