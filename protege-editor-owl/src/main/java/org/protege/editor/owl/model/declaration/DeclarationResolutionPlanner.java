package org.protege.editor.owl.model.declaration;

import com.google.common.collect.ImmutableList;
import org.semanticweb.owlapi.model.*;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Plans declaration-only repairs without modifying an ontology.
 *
 * <p>Reuses the manual report's checkers to reject stale findings. Missing declarations go to an
 * explicitly chosen ontology; misplaced declarations go to the owner recorded by the checker.
 * All declaration annotations are retained when a declaration is moved.
 */
public final class DeclarationResolutionPlanner {

    private final OWLOntology root;
    private final Predicate<OWLOntology> editable;

    public DeclarationResolutionPlanner(OWLOntology root, Predicate<OWLOntology> editable) {
        this.root = checkNotNull(root);
        this.editable = checkNotNull(editable);
    }

    /**
     * Plans one batch of repairs. Every finding and affected ontology must still be valid.
     * Nothing is changed if planning fails; callers apply the returned changes through Protege's
     * model manager so edits participate in the normal history and save workflow.
     *
     * @param missing selected missing findings
     * @param misplaced selected misplaced findings
     * @param missingTarget the destination for missing declarations; unused when none are selected
     * @return an immutable batch, with destination additions ordered before source removals
     * @throws IllegalStateException if the report is stale or an affected ontology is not editable
     */
    public Plan plan(Collection<MissingDeclarationFinding> missing,
                     Collection<MisplacedDeclarationFinding> misplaced,
                     OWLOntology missingTarget) {
        DeclarationReport current = DeclarationChecker.forcingAllChecks().check(root);
        require(new HashSet<>(current.getMissing().getFindings()).containsAll(missing)
                        && new HashSet<>(current.getMisplaced().getFindings()).containsAll(misplaced),
                "The declaration report has changed. Refresh it and select the findings again.");
        Map<OWLOntologyID, OWLOntology> closure = new HashMap<>();
        root.getImportsClosure().forEach(ontology -> closure.put(ontology.getOntologyID(), ontology));
        Set<OWLOntology> checked = new HashSet<>();
        Set<OWLOntologyChange> additions = new LinkedHashSet<>();
        Set<OWLOntologyChange> removals = new LinkedHashSet<>();
        if (!missing.isEmpty()) {
            requireEditable(missingTarget, closure, checked);
            OWLDataFactory factory = root.getOWLOntologyManager().getOWLDataFactory();
            for (MissingDeclarationFinding finding : missing) {
                additions.add(new AddAxiom(missingTarget, factory.getOWLDeclarationAxiom(finding.getEntity())));
            }
        }
        for (MisplacedDeclarationFinding finding : misplaced) {
            OWLOntology target = ontologyIn(closure, finding.getOwningOntology());
            requireEditable(target, closure, checked);
            for (OWLOntologyID sourceId : finding.getDeclaringOntologies()) {
                OWLOntology source = ontologyIn(closure, sourceId);
                requireEditable(source, closure, checked);
                for (OWLDeclarationAxiom declaration : source.getDeclarationAxioms(finding.getEntity())) {
                    // Copy the actual axiom, including annotations, rather than reconstructing it.
                    if (!target.containsAxiom(declaration)) {
                        additions.add(new AddAxiom(target, declaration));
                    }
                    removals.add(new RemoveAxiom(source, declaration));
                }
            }
        }
        return new Plan(ImmutableList.<OWLOntologyChange>builder()
                .addAll(additions).addAll(removals).build(),
                new LinkedHashSet<>(missing).size() + new LinkedHashSet<>(misplaced).size());
    }

    private void requireEditable(OWLOntology ontology, Map<OWLOntologyID, OWLOntology> closure,
                                 Set<OWLOntology> checked) {
        require(ontology != null && ontology.equals(closure.get(ontology.getOntologyID()))
                        && ontology.getOWLOntologyManager() == root.getOWLOntologyManager(),
                "A destination or source ontology is no longer in the report's import closure. Refresh the report.");
        if (checked.add(ontology)) {
            require(editable.test(ontology), "The ontology is not editable: "
                    + OntologyIdFormat.format(ontology.getOntologyID()));
        }
    }

    private static OWLOntology ontologyIn(Map<OWLOntologyID, OWLOntology> closure, OWLOntologyID id) {
        OWLOntology ontology = closure.get(id);
        require(ontology != null,
                "An ontology is no longer loaded in the report's import closure. Refresh the report.");
        return ontology;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    /** An immutable command batch that can be previewed before applying it. */
    public static final class Plan {

        private final ImmutableList<OWLOntologyChange> changes;
        private final int findingCount;

        private Plan(ImmutableList<OWLOntologyChange> changes, int findingCount) {
            this.changes = changes;
            this.findingCount = findingCount;
        }

        public List<OWLOntologyChange> getChanges() {
            return changes;
        }

        public int getFindingCount() {
            return findingCount;
        }
    }
}
