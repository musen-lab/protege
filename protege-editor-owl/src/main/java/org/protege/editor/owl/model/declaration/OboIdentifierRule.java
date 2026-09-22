package org.protege.editor.owl.model.declaration;

import org.protege.editor.owl.model.util.OboUtilities;
import org.semanticweb.owlapi.model.OWLOntologyID;

import javax.annotation.Nonnull;
import java.util.Locale;
import java.util.Optional;

/**
 * Decides the owner of an OBO entity by matching its ID space to an ontology's short name.
 *
 * <p>The ID space is the part of an OBO identifier before the final underscore and numeric local
 * ID. For example, {@code GO_0006915} has the ID space {@code GO}, while
 * {@code APOLLO_SV_0000001} has the ID space {@code APOLLO_SV}. The ontology short name is the last
 * path segment of its primary ontology IRI with a final {@code .owl} removed. Consequently,
 * {@code GO_0006915} can match an ontology whose IRI ends in {@code /go.owl}, including a mirror or
 * a dated release URL.
 *
 * <p>Matching is case-insensitive, so mixed-case ID spaces such as {@code NCBITaxon} match
 * lower-case ontology file names. Anonymous ontologies and ontology IRIs ending in {@code /} have
 * no usable short name. If two different candidate ontologies have the same short name, the match
 * is ambiguous and the rule selects neither one.
 *
 * <p>This rule is separate from {@link NamespaceRule} because OBO entities from different
 * ontologies normally share the same {@code http://purl.obolibrary.org/obo/} IRI namespace.
 *
 * @author Josef Hardi
 */
final class OboIdentifierRule implements OwnershipRule {

    /** Stable identifier used to store this rule's preference and record its findings. */
    static final String ID = "misplaced.rule.use.obo.identifier";

    private static final String OWL_SUFFIX = ".owl";

    @Nonnull
    private static final OwnershipRuleDisplay DISPLAY = OwnershipRuleDisplay.get(
            "Decide ownership from the OBO ID spaces",
            "<html>Matches the ID space in an OBO ID to an ontology's short name. "
                    + "For example, <b>GO_0006915</b> matches <b>go.owl</b>."
                    + "<br><br>This rule is needed for OBO terms because they share the same "
                    + "IRI namespace.</html>");

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
        OntologyKeyIndex byShortName = OntologyKeyIndex.over(closure.getOntologies(), OboIdentifierRule::shortNameOf);
        // OboUtilities reads an ID space that contains an underscore, such as APOLLO_SV, correctly.
        return entity -> OboUtilities.getOboIdSpaceFromIri(entity.getIRI())
                .map(idSpace -> idSpace.toLowerCase(Locale.ROOT))
                .flatMap(byShortName::lookup);
    }

    /**
     * Derives the case-insensitive matching key from an ontology's primary IRI.
     *
     * <p>The key is the final path segment, without a final {@code .owl}, converted to lower case.
     * An anonymous ontology produces an empty optional. An IRI ending in {@code /} produces an
     * empty key, which {@link OntologyKeyIndex} excludes from its index.
     */
    private static Optional<String> shortNameOf(OWLOntologyID ontology) {
        return OntologyIris.ontologyIriOf(ontology).map(ontologyIri -> {
            int lastSlash = ontologyIri.lastIndexOf('/');
            String segment = lastSlash < 0 ? ontologyIri : ontologyIri.substring(lastSlash + 1);
            if (segment.endsWith(OWL_SUFFIX)) {
                segment = segment.substring(0, segment.length() - OWL_SUFFIX.length());
            }
            return segment.toLowerCase(Locale.ROOT);
        });
    }
}
