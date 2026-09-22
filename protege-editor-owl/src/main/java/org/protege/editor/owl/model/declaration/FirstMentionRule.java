package org.protege.editor.owl.model.declaration;

import com.google.common.collect.ImmutableSet;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLOntologyID;

import javax.annotation.Nonnull;
import java.util.Optional;

/**
 * Selects an entity's owner from the first ontology in the import chain that mentions it.
 *
 * <p>"First" is defined by the direction of the imports, not by iteration order. When one
 * mentioning ontology imports another, the imported ontology comes first. For example, if
 * {@code application.owl} imports {@code core.owl} and both ontologies mention {@code Customer},
 * this rule selects {@code core.owl}. If only {@code application.owl} mentions {@code Customer},
 * it selects {@code application.owl} instead. A mention is any use in an ontology's own axioms,
 * not necessarily a declaration.
 *
 * <p>The rule selects an owner only when there is one unique first mention. It returns no owner
 * when the earliest mentions occur in two sibling ontologies, because neither imports the other.
 * It also returns no owner when the first mentions are connected by an import cycle, because the
 * cycle gives them equal standing.
 *
 * <p>Unlike {@link NamespaceRule} and {@link OboIdentifierRule}, this rule does not inspect the
 * entity or ontology IRIs. It can therefore resolve ownership in projects whose entities share a
 * namespace that does not identify any one ontology.
 *
 * @author Josef Hardi
 */
final class FirstMentionRule implements OwnershipRule {

    /** Stable identifier used to store this rule's preference and record its findings. */
    static final String ID = "misplaced.rule.use.first.mention";

    @Nonnull
    private static final OwnershipRuleDisplay DISPLAY = OwnershipRuleDisplay.get(
            "Decide ownership from the import closure",
            "<html>Matches an entity to the first document in the import closure that mentions it. "
                    + "For example, a term used in <b>core.owl</b> and declared in <b>sales.owl</b> "
                    + "belongs to <b>core.owl</b>."
                    + "<br><br>Use this rule when entity IRIs are not built from the ontology IRI, "
                    + "so neither of the other rules can decide ownership. It reports nothing when "
                    + "two documents mention a term side by side.</html>");

    @Override
    @Nonnull
    public String getId() {
        return ID;
    }

    @Override
    @Nonnull
    public OwnershipRuleDisplay getDisplay() {
        return DISPLAY;
    }

    @Override
    @Nonnull
    public Resolver compile(@Nonnull ImportClosureView closure) {
        return entity -> ownerIn(closure, entity);
    }

    @Override
    public boolean standsInForOwner(@Nonnull OWLOntologyID owner, @Nonnull OWLOntologyID candidate) {
        return false;
    }

    /**
     * Finds the unique mentioning ontology with no earlier mention below it in the import graph.
     *
     * @param closure the import closure to search
     * @param entity the entity whose owner is required
     * @return the unique first mention, or an empty optional if the entity is not mentioned or its
     *         first mention is ambiguous
     */
    @Nonnull
    private static Optional<OWLOntologyID> ownerIn(ImportClosureView closure, OWLEntity entity) {
        ImmutableSet<OWLOntologyID> mentioners = closure.getMentioningOntologies(entity);
        if (mentioners.size() == 1) {
            return Optional.of(mentioners.iterator().next());
        }
        OWLOntologyID first = null;
        for (OWLOntologyID candidate : mentioners) {
            if (nothingMentioningIsBelow(closure, candidate, mentioners)) {
                if (first != null) {
                    return Optional.empty();    // two documents are equally first, so neither owns it
                }
                first = candidate;
            }
        }
        return Optional.ofNullable(first);
    }

    /** Checks that no other mentioning ontology occurs earlier in the import direction. */
    private static boolean nothingMentioningIsBelow(ImportClosureView closure,
                                                    OWLOntologyID candidate,
                                                    ImmutableSet<OWLOntologyID> mentioners) {
        for (OWLOntologyID other : mentioners) {
            if (!other.equals(candidate) && isBelow(closure, other, candidate)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Tests whether {@code lower} is strictly below {@code upper} in the import graph.
     *
     * <p>The relation is strict because {@code lower} must not also reach {@code upper}. Ontologies
     * in an import cycle reach each other, so neither is below the other.
     */
    private static boolean isBelow(ImportClosureView closure, OWLOntologyID lower, OWLOntologyID upper) {
        return closure.reaches(upper, lower) && !closure.reaches(lower, upper);
    }
}
