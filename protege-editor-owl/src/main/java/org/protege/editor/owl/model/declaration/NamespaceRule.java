package org.protege.editor.owl.model.declaration;

import org.semanticweb.owlapi.model.OWLOntologyID;

import javax.annotation.Nonnull;

/**
 * Decides an entity's owner by matching the entity IRI namespace to an ontology IRI.
 *
 * <p>The rule obtains the namespace from the entity IRI and compares it with the primary ontology
 * IRI of each candidate ontology. It removes one final {@code #} or {@code /} from both values
 * before performing an exact string comparison. For example, the namespace of
 * {@code http://example.org/base#Term} matches the ontology IRI
 * {@code http://example.org/base}.
 *
 * <p>The version IRI of an ontology is ignored. Anonymous ontologies are excluded because they
 * have no ontology IRI. If two different candidate ontologies have the same normalized ontology
 * IRI, the match is ambiguous and the rule selects neither one.
 *
 * @author Josef Hardi
 */
final class NamespaceRule implements OwnershipRule {

    /** Stable identifier used to store this rule's preference and record its findings. */
    static final String ID = "misplaced.rule.use.namespace";

    @Nonnull
    private static final OwnershipRuleDisplay DISPLAY = OwnershipRuleDisplay.get(
            "Decide ownership from the IRI namespace",
            "<html>Matches an entity's IRI namespace to an ontology IRI. "
                    + "For example, <b>http://example.org/base#Term</b> matches "
                    + "<b>http://example.org/base</b>."
                    + "<br><br>Use this rule when entity IRIs are based on the ontology IRI.</html>");

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
        OntologyKeyIndex byOntologyIri = OntologyKeyIndex.over(closure.getOntologies(),
                ontology ->
                        OntologyIris.ontologyIriOf(ontology)
                                    .map(OntologyIris::stripTrailingSeparator));
        return entity -> byOntologyIri.lookup(
                OntologyIris.stripTrailingSeparator(entity.getIRI().getNamespace()));
    }
}
