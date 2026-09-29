package org.protege.editor.owl.model.declaration;

import com.google.common.collect.ImmutableSet;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyID;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A minimal, hand-built {@link ImportClosureView} for ownership-rule unit tests.
 *
 * <p>The fixture stores exactly the ontology, mention, and reachability facts supplied by the test.
 * It does not inspect ontology axioms, follow imports, or infer transitive and cyclic reachability.
 * This keeps tests focused when they need controlled closure facts rather than their production
 * derivation.
 *
 * <p>Tests whose behavior depends on deriving mentions from axioms or reachability from imports
 * should use a real {@link DeclarationIndex}. Otherwise the test would repeat that production
 * logic in its setup instead of exercising it.
 */
final class TestClosureView implements ImportClosureView {

    private final ImmutableSet<OWLOntologyID> ontologies;

    private final Map<OWLEntity, Set<OWLOntologyID>> mentions = new HashMap<>();

    private final Map<OWLOntologyID, Set<OWLOntologyID>> reaches = new HashMap<>();

    private TestClosureView(ImmutableSet<OWLOntologyID> ontologies) {
        this.ontologies = ontologies;
    }

    /**
     * Creates a view containing exactly the supplied ontologies.
     *
     * <p>The new view initially records no entity mentions and no reachability between ontologies.
     *
     * @param ontologies the ontologies whose identifiers the view contains
     * @return an otherwise empty view of the supplied ontologies
     */
    static TestClosureView over(OWLOntology... ontologies) {
        return new TestClosureView(ImmutableSet.copyOf(Arrays.stream(ontologies)
                .map(OWLOntology::getOntologyID)
                .collect(Collectors.toList())));
    }

    /**
     * Adds the fact that each supplied ontology mentions an entity.
     *
     * @param entity the entity being mentioned
     * @param mentioners the ontologies that mention the entity
     * @return this view, for further configuration
     */
    TestClosureView mentioning(OWLEntity entity, OWLOntology... mentioners) {
        mentions.computeIfAbsent(entity, key -> new HashSet<>())
                .addAll(Arrays.stream(mentioners)
                        .map(OWLOntology::getOntologyID)
                        .collect(Collectors.toSet()));
        return this;
    }

    /**
     * Adds directed reachability facts from one ontology to the supplied targets.
     *
     * <p>This method does not infer transitive, reverse, or self-reachability. The test must add
     * every relation it expects the view to report.
     *
     * @param from the ontology from which the targets are reachable
     * @param targets the ontologies reachable from {@code from}
     * @return this view, for further configuration
     */
    TestClosureView reaching(OWLOntology from, OWLOntology... targets) {
        reaches.computeIfAbsent(from.getOntologyID(), key -> new HashSet<>())
                .addAll(Arrays.stream(targets)
                        .map(OWLOntology::getOntologyID)
                        .collect(Collectors.toSet()));
        return this;
    }

    @Override
    public ImmutableSet<OWLOntologyID> getOntologies() {
        return ontologies;
    }

    @Override
    public ImmutableSet<OWLOntologyID> getMentioningOntologies(OWLEntity entity) {
        return ImmutableSet.copyOf(mentions.getOrDefault(entity, ImmutableSet.of()));
    }

    @Override
    public boolean reaches(OWLOntologyID from, OWLOntologyID to) {
        return reaches.getOrDefault(from, ImmutableSet.of()).contains(to);
    }
}
