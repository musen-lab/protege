package org.protege.editor.owl.model.declaration;

import org.junit.Before;
import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import static org.junit.Assert.*;

public class DeclarationResolutionPlanner_TestCase {

    private OWLOntologyManager manager;
    private OWLDataFactory factory;
    private OWLOntology root;
    private OWLOntology owner;
    private OWLClass entity;

    @Before
    public void setUp() throws Exception {
        manager = OWLManager.createOWLOntologyManager();
        factory = manager.getOWLDataFactory();
        root = manager.createOntology(IRI.create("http://example.org/root"));
        owner = imported("http://example.org/owner");
        entity = factory.getOWLClass(IRI.create("http://example.org/owner#Term"));
    }

    @Test
    public void shouldPlanMissingInChosenOntologyWithoutMutatingIt() {
        manager.addAxiom(root, factory.getOWLSubClassOfAxiom(entity, factory.getOWLThing()));
        DeclarationReport report = check();
        DeclarationResolutionPlanner.Plan plan = planner().plan(report.getMissing().getFindings(),
                Collections.emptyList(), Collections.singletonMap(entity, owner));
        assertEquals(1, plan.getFindingCount());
        assertEquals(Collections.singletonList(new AddAxiom(owner, factory.getOWLDeclarationAxiom(entity))),
                plan.getChanges());
        assertTrue(owner.getDeclarationAxioms(entity).isEmpty());
        manager.applyChanges(plan.getChanges());
        assertTrue(check().isEmpty());
    }

    @Test
    public void shouldMoveEveryAnnotatedDeclarationAndLeaveNonDeclarationAxiomsAlone() throws Exception {
        OWLOntology anotherSource = imported("http://example.org/another");
        OWLAnnotation note = factory.getOWLAnnotation(factory.getRDFSLabel(), factory.getOWLLiteral("keep me"));
        OWLDeclarationAxiom annotated = factory.getOWLDeclarationAxiom(entity, Collections.singleton(note));
        OWLDeclarationAxiom plain = factory.getOWLDeclarationAxiom(entity);
        OWLAxiom use = factory.getOWLSubClassOfAxiom(entity, factory.getOWLThing());
        manager.addAxiom(root, annotated);
        manager.addAxiom(root, plain);
        manager.addAxiom(root, use);
        manager.addAxiom(anotherSource, annotated);
        DeclarationResolutionPlanner.Plan plan = planner().plan(Collections.emptyList(),
                check().getMisplaced().getFindings(), Collections.singletonMap(entity, owner));
        assertEquals(1, plan.getFindingCount());
        assertEquals(5, plan.getChanges().size());
        assertTrue(plan.getChanges().get(0).isAddAxiom());
        assertTrue(plan.getChanges().get(1).isAddAxiom());
        manager.applyChanges(plan.getChanges());
        assertTrue(owner.containsAxiom(annotated));
        assertTrue(owner.containsAxiom(plain));
        assertTrue(root.getDeclarationAxioms(entity).isEmpty());
        assertTrue(anotherSource.getDeclarationAxioms(entity).isEmpty());
        assertTrue(root.containsAxiom(use));
        assertTrue(check().isEmpty());
    }

    @Test
    public void shouldResolveMixedSelectionAsOneBatchAndKeepOtherFindings() {
        OWLClass missing = factory.getOWLClass(IRI.create("http://example.org/owner#Missing"));
        OWLClass unselected = factory.getOWLClass(IRI.create("http://example.org/owner#Other"));
        manager.addAxiom(root, factory.getOWLSubClassOfAxiom(missing, unselected));
        manager.addAxiom(root, factory.getOWLDeclarationAxiom(entity));
        DeclarationReport report = check();
        MissingDeclarationFinding selected = report.getMissing().getFindings().stream()
                .filter(finding -> finding.getEntity().equals(missing)).findFirst().get();
        Map<OWLEntity, OWLOntology> targets = new HashMap<>();
        targets.put(missing, owner);
        targets.put(entity, owner);
        DeclarationResolutionPlanner.Plan plan = planner().plan(Collections.singletonList(selected),
                report.getMisplaced().getFindings(), targets);
        assertEquals(2, plan.getFindingCount());
        manager.applyChanges(plan.getChanges());
        assertEquals(1, check().getMissing().size());
        assertEquals(unselected, check().getMissing().getFindings().get(0).getEntity());
        assertTrue(check().getMisplaced().isEmpty());
    }

    @Test
    public void shouldRetainEntityTypeWhenClassAndIndividualShareAnIri() {
        OWLNamedIndividual individual = factory.getOWLNamedIndividual(entity.getIRI());
        manager.addAxiom(root, factory.getOWLClassAssertionAxiom(entity, individual));
        Map<OWLEntity, OWLOntology> targets = new HashMap<>();
        targets.put(entity, owner);
        targets.put(individual, owner);
        DeclarationResolutionPlanner.Plan plan = planner().plan(check().getMissing().getFindings(),
                Collections.emptyList(), targets);
        assertEquals(2, plan.getFindingCount());
        manager.applyChanges(plan.getChanges());
        assertFalse(owner.getDeclarationAxioms(entity).isEmpty());
        assertFalse(owner.getDeclarationAxioms(individual).isEmpty());
        assertTrue(check().isEmpty());
    }

    @Test
    public void shouldRejectReadOnlyDestinationBeforeAnyEdits() {
        manager.addAxiom(root, factory.getOWLSubClassOfAxiom(entity, factory.getOWLThing()));
        assertThrows(IllegalStateException.class, () -> new DeclarationResolutionPlanner(root, ontology -> false)
                .plan(check().getMissing().getFindings(), Collections.emptyList(),
                        Collections.singletonMap(entity, owner)));
        assertTrue(owner.getDeclarationAxioms(entity).isEmpty());
    }

    @Test
    public void shouldRejectReadOnlySourceBeforeAnyEdits() {
        manager.addAxiom(root, factory.getOWLDeclarationAxiom(entity));
        assertThrows(IllegalStateException.class, () -> new DeclarationResolutionPlanner(root, ontology -> !ontology.equals(root))
                .plan(Collections.emptyList(), check().getMisplaced().getFindings(),
                        Collections.singletonMap(entity, owner)));
        assertFalse(root.getDeclarationAxioms(entity).isEmpty());
        assertTrue(owner.getDeclarationAxioms(entity).isEmpty());
    }

    @Test
    public void shouldRejectDestinationOutsideClosure() throws Exception {
        OWLOntology unrelated = manager.createOntology();
        manager.addAxiom(root, factory.getOWLSubClassOfAxiom(entity, factory.getOWLThing()));
        assertThrows(IllegalStateException.class, () -> planner().plan(check().getMissing().getFindings(),
                Collections.emptyList(), Collections.singletonMap(entity, unrelated)));
    }

    @Test
    public void shouldRejectStaleMissingFindingAndRepeatedRepair() {
        manager.addAxiom(root, factory.getOWLSubClassOfAxiom(entity, factory.getOWLThing()));
        DeclarationReport report = check();
        DeclarationResolutionPlanner.Plan plan = planner().plan(report.getMissing().getFindings(),
                Collections.emptyList(), Collections.singletonMap(entity, owner));
        manager.applyChanges(plan.getChanges());
        assertThrows(IllegalStateException.class, () -> planner().plan(report.getMissing().getFindings(),
                Collections.emptyList(), Collections.singletonMap(entity, owner)));
    }

    @Test
    public void shouldRejectStaleMisplacedFindingWhenImportIsRemoved() {
        manager.addAxiom(root, factory.getOWLDeclarationAxiom(entity));
        DeclarationReport report = check();
        manager.applyChange(new RemoveImport(root, factory.getOWLImportsDeclaration(
                owner.getOntologyID().getOntologyIRI().get())));
        assertThrows(IllegalStateException.class, () -> planner().plan(Collections.emptyList(),
                report.getMisplaced().getFindings(), Collections.singletonMap(entity, owner)));
        assertFalse(root.getDeclarationAxioms(entity).isEmpty());
    }

    @Test
    public void shouldDeduplicateSelectionsAndExposeImmutableChanges() {
        manager.addAxiom(root, factory.getOWLSubClassOfAxiom(entity, factory.getOWLThing()));
        MissingDeclarationFinding finding = check().getMissing().getFindings().get(0);
        DeclarationResolutionPlanner.Plan plan = planner().plan(Arrays.asList(finding, finding),
                Collections.emptyList(), Collections.singletonMap(entity, owner));
        assertEquals(1, plan.getFindingCount());
        assertEquals(1, plan.getChanges().size());
        assertThrows(UnsupportedOperationException.class, () -> plan.getChanges().clear());
    }

    @Test
    public void shouldAllowEmptySelectionWithoutATarget() {
        assertTrue(planner().plan(Collections.emptyList(), Collections.emptyList(),
                Collections.emptyMap()).getChanges().isEmpty());
    }

    @Test
    public void shouldBeReversibleUsingOwlApiChanges() {
        manager.addAxiom(root, factory.getOWLDeclarationAxiom(entity));
        DeclarationResolutionPlanner.Plan plan = planner().plan(Collections.emptyList(),
                check().getMisplaced().getFindings(), Collections.singletonMap(entity, owner));
        manager.applyChanges(plan.getChanges());
        List<OWLOntologyChange> reverse = new java.util.ArrayList<>();
        for (OWLOntologyChange change : plan.getChanges()) {
            reverse.add(change.reverseChange());
        }
        Collections.reverse(reverse);
        manager.applyChanges(reverse);
        assertFalse(root.getDeclarationAxioms(entity).isEmpty());
        assertTrue(owner.getDeclarationAxioms(entity).isEmpty());
    }

    @Test(timeout = 30_000)
    public void shouldPlanAndResolveTwentyThousandFindingsInALargeOntology() {
        int count = 10_000;
        java.util.Set<OWLAxiom> sourceAxioms = new java.util.HashSet<>();
        java.util.Set<OWLAxiom> ownerAxioms = new java.util.HashSet<>();
        Map<OWLEntity, OWLOntology> targets = new HashMap<>();
        for (int i = 0; i < 100_000; i++) {
            OWLClass term = factory.getOWLClass(IRI.create("http://example.org/owner#Scale" + i));
            if (i < count) {
                sourceAxioms.add(factory.getOWLSubClassOfAxiom(term, factory.getOWLThing()));
                targets.put(term, owner);
            } else if (i < count * 2) {
                sourceAxioms.add(factory.getOWLDeclarationAxiom(term));
                targets.put(term, owner);
            } else {
                ownerAxioms.add(factory.getOWLDeclarationAxiom(term));
            }
        }
        manager.addAxioms(root, sourceAxioms);
        manager.addAxioms(owner, ownerAxioms);
        DeclarationReport report = check();
        assertEquals(count, report.getMissing().size());
        assertEquals(count, report.getMisplaced().size());
        java.util.concurrent.atomic.AtomicInteger permissionChecks = new java.util.concurrent.atomic.AtomicInteger();
        long start = System.nanoTime();
        DeclarationResolutionPlanner.Plan plan = new DeclarationResolutionPlanner(root, ontology -> {
            permissionChecks.incrementAndGet();
            return true;
        }).plan(report.getMissing().getFindings(), report.getMisplaced().getFindings(), targets);
        long planned = System.nanoTime();
        assertEquals(count * 2, plan.getFindingCount());
        assertEquals(count * 3, plan.getChanges().size());
        assertEquals(2, permissionChecks.get());
        manager.applyChanges(plan.getChanges());
        assertTrue(check().isEmpty());
        System.out.printf("100,000 entities / 20,000 findings: planning %.3fs, apply and recheck %.3fs%n",
                (planned - start) / 1e9, (System.nanoTime() - planned) / 1e9);
    }

    @Test
    public void shouldUseAnIndividualDestinationForEachPunnedEntity() {
        OWLNamedIndividual individual = factory.getOWLNamedIndividual(entity.getIRI());
        manager.addAxiom(root, factory.getOWLClassAssertionAxiom(entity, individual));
        java.util.Map<OWLEntity, OWLOntology> targets = new java.util.HashMap<>();
        targets.put(entity, root);
        targets.put(individual, owner);
        DeclarationResolutionPlanner.Plan plan = planner().plan(check().getMissing().getFindings(),
                Collections.emptyList(), targets);
        manager.applyChanges(plan.getChanges());
        assertFalse(root.getDeclarationAxioms(entity).isEmpty());
        assertTrue(owner.getDeclarationAxioms(entity).isEmpty());
        assertFalse(owner.getDeclarationAxioms(individual).isEmpty());
        assertTrue(root.getDeclarationAxioms(individual).isEmpty());
    }

    @Test
    public void shouldRejectAnIncompleteSetOfIndividualChoicesBeforeApplyingAnyRepairs() {
        manager.addAxiom(root, factory.getOWLSubClassOfAxiom(entity, factory.getOWLThing()));
        assertThrows(IllegalStateException.class, () -> planner().plan(
                check().getMissing().getFindings(), Collections.emptyList(), Collections.emptyMap()));
        assertTrue(root.getDeclarationAxioms(entity).isEmpty());
        assertTrue(owner.getDeclarationAxioms(entity).isEmpty());
    }

    @Test
    public void shouldMoveMisplacedAxiomsToTheChosenDestinationInsteadOfTheSuggestion() throws Exception {
        OWLOntology chosen = imported("http://example.org/chosen");
        OWLDeclarationAxiom declaration = factory.getOWLDeclarationAxiom(entity, Collections.singleton(
                factory.getOWLAnnotation(factory.getRDFSLabel(), factory.getOWLLiteral("preserve me"))));
        manager.addAxiom(root, declaration);
        DeclarationResolutionPlanner.Plan plan = planner().plan(Collections.emptyList(),
                check().getMisplaced().getFindings(), Collections.singletonMap(entity, chosen));
        manager.applyChanges(plan.getChanges());
        assertTrue(chosen.containsAxiom(declaration));
        assertTrue(root.getDeclarationAxioms(entity).isEmpty());
        assertTrue(owner.getDeclarationAxioms(entity).isEmpty());
    }

    @Test
    public void shouldNotRemoveDeclarationsAlreadyInTheChosenSourceOntology() throws Exception {
        OWLOntology another = imported("http://example.org/another");
        OWLDeclarationAxiom original = factory.getOWLDeclarationAxiom(entity);
        OWLDeclarationAxiom annotated = factory.getOWLDeclarationAxiom(entity, Collections.singleton(
                factory.getOWLAnnotation(factory.getRDFSLabel(), factory.getOWLLiteral("preserve me"))));
        manager.addAxiom(root, original);
        manager.addAxiom(another, annotated);
        DeclarationResolutionPlanner.Plan plan = planner().plan(Collections.emptyList(),
                check().getMisplaced().getFindings(), Collections.singletonMap(entity, root));
        manager.applyChanges(plan.getChanges());
        assertTrue(root.containsAxiom(original));
        assertTrue(root.containsAxiom(annotated));
        assertTrue(another.getDeclarationAxioms(entity).isEmpty());
    }

    @Test
    public void shouldRejectAnUneditableOrMissingChosenMisplacedDestination() {
        manager.addAxiom(root, factory.getOWLDeclarationAxiom(entity));
        List<MisplacedDeclarationFinding> findings = check().getMisplaced().getFindings();
        assertThrows(IllegalStateException.class, () -> planner().plan(
                Collections.emptyList(), findings, Collections.emptyMap()));
        assertThrows(IllegalStateException.class, () -> new DeclarationResolutionPlanner(root, ontology -> false)
                .plan(Collections.emptyList(), findings, Collections.singletonMap(entity, owner)));
        assertFalse(root.getDeclarationAxioms(entity).isEmpty());
        assertTrue(owner.getDeclarationAxioms(entity).isEmpty());
    }

    @Test
    public void shouldRejectAStoredPlacementAfterTheImportGraphChangesWithoutChangingMentions() throws Exception {
        OWLOntology sibling = imported("http://example.org/sibling");
        manager.addAxiom(owner, factory.getOWLSubClassOfAxiom(entity, factory.getOWLThing()));
        manager.addAxiom(sibling, factory.getOWLSubClassOfAxiom(entity, factory.getOWLThing()));
        MissingDeclarationFinding before = check().getMissing().getFindings().get(0);
        assertFalse(before.getPlacementOntology().isPresent());
        manager.applyChange(new AddImport(sibling, factory.getOWLImportsDeclaration(
                owner.getOntologyID().getOntologyIRI().get())));
        MissingDeclarationFinding after = check().getMissing().getFindings().get(0);
        assertEquals(before.getMentioningOntologies(), after.getMentioningOntologies());
        assertEquals(java.util.Optional.of(owner.getOntologyID()), after.getPlacementOntology());
        assertThrows(IllegalStateException.class, () -> planner().plan(Collections.singletonList(before),
                Collections.emptyList(), Collections.singletonMap(entity, owner)));
        assertTrue(owner.getDeclarationAxioms(entity).isEmpty());
    }

    private OWLOntology imported(String iri) throws Exception {
        OWLOntology ontology = manager.createOntology(IRI.create(iri));
        manager.applyChange(new AddImport(root, factory.getOWLImportsDeclaration(IRI.create(iri))));
        return ontology;
    }

    private DeclarationReport check() {
        return DeclarationChecker.forcingAllChecks().check(root);
    }

    private DeclarationResolutionPlanner planner() {
        return new DeclarationResolutionPlanner(root, ontology -> true);
    }
}
