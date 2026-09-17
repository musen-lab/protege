package org.protege.editor.owl.model.declaration;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLOntologyID;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Selects the ontology that is expected to own an entity, using an ordered list of ownership rules.
 *
 * <p>A policy is created for one collection of candidate ontologies, normally an import closure.
 * Each enabled {@link OwnershipRule} is compiled against those candidates once, and the resulting
 * resolvers are reused for every entity examined during the run.
 *
 * <p>Rule order defines priority. When {@link #resolve(OWLEntity)} is called, the policy consults
 * each rule in order and stops at the first rule that returns an ontology. The result records both
 * that ontology and the identifier of the rule that selected it. Rules not supplied when the
 * policy is created have no effect.
 *
 * <p>This class determines the expected owner only. It does not inspect declarations or decide
 * whether an entity is misplaced.
 *
 * @author Josef Hardi
 */
final class OwnershipPolicy {

    @Nonnull
    private final ImmutableList<CompiledRule> compiledRules;

    private OwnershipPolicy(@Nonnull ImmutableList<CompiledRule> compiledRules) {
        this.compiledRules = compiledRules;
    }

    /**
     * Creates a policy by compiling each supplied rule against the same candidate ontologies.
     *
     * <p>The iteration order of {@code rules} becomes the precedence order used by
     * {@link #resolve(OWLEntity)}. Supplying no rules creates a policy that never resolves an
     * owner.
     *
     * @param rules the enabled rules, in decreasing precedence order
     * @param ontologies the ontology identifiers that each rule may select as an owner
     * @return a policy whose compiled resolvers can be reused for multiple entities
     * @throws NullPointerException if {@code rules} or {@code ontologies} is {@code null}
     */
    @Nonnull
    static OwnershipPolicy over(@Nonnull Collection<OwnershipRule> rules,
                                @Nonnull Collection<OWLOntologyID> ontologies) {
        checkNotNull(rules);
        checkNotNull(ontologies);
        ImmutableList.Builder<CompiledRule> compiled = ImmutableList.builder();
        for (OwnershipRule rule : rules) {
            compiled.add(new CompiledRule(rule.getId(), rule.compile(ontologies)));
        }
        return new OwnershipPolicy(compiled.build());
    }

    /**
     * Selects the expected owner of an entity.
     *
     * <p>Compiled rules are consulted in precedence order. The first rule to return an ontology
     * wins, and no lower-precedence rules are consulted. An empty result means that none of the
     * enabled rules could determine an owner; it does not mean that the entity has no declarations.
     *
     * @param entity the entity whose identifier is to be resolved
     * @return the selected ontology and the identifier of the rule that selected it, or an empty
     *         optional if no rule determines an owner
     * @throws NullPointerException if {@code entity} is {@code null}
     */
    @Nonnull
    Optional<DeclarationOwner> resolve(@Nonnull OWLEntity entity) {
        checkNotNull(entity);
        for (CompiledRule compiledRule : compiledRules) {
            Optional<OWLOntologyID> owner = compiledRule.resolver.resolve(entity);
            if (owner.isPresent()) {
                return Optional.of(DeclarationOwner.get(owner.get(), compiledRule.ruleId));
            }
        }
        return Optional.empty();
    }

    /** Associates a compiled resolver with the identifier of the rule that produced it. */
    private static final class CompiledRule {

        private final String ruleId;

        private final OwnershipRule.Resolver resolver;

        private CompiledRule(String ruleId, OwnershipRule.Resolver resolver) {
            this.ruleId = checkNotNull(ruleId);
            this.resolver = checkNotNull(resolver);
        }
    }
}
