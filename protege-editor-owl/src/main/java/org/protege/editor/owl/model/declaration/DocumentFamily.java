package org.protege.editor.owl.model.declaration;

import org.semanticweb.owlapi.model.OWLOntologyID;

import javax.annotation.Nonnull;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Checks whether two ontologies are documents from the same project.
 *
 * <p>A document belongs to the same family as a parent document when its IRI is below the parent's
 * path. For example, {@code go/components/terms.owl} belongs to {@code go.owl}.
 *
 * @author Josef Hardi
 */
final class DocumentFamily {

    private static final String OWL_SUFFIX = ".owl";

    private DocumentFamily() {
    }

    /**
     * Checks whether two ontologies belong to the same document family.
     *
     * <p>An ontology matches itself. An anonymous ontology does not match any other ontology.
     *
     * @param one the identifier of the first ontology
     * @param other the identifier of the second ontology
     * @return {@code true} if the two ontologies belong to the same family, otherwise
     *         {@code false}
     */
    static boolean sameFamily(@Nonnull OWLOntologyID one, @Nonnull OWLOntologyID other) {
        checkNotNull(one);
        checkNotNull(other);
        if (one.equals(other)) {
            return true;
        }
        Optional<String> oneIri = OntologyIris.ontologyIriOf(one);
        Optional<String> otherIri = OntologyIris.ontologyIriOf(other);
        if (oneIri.isEmpty() || otherIri.isEmpty()) {
            return false;
        }
        // Read in both directions, so the relationship holds whichever document owns the namespace.
        return isBeneath(otherIri.get(), oneIri.get()) || isBeneath(oneIri.get(), otherIri.get());
    }

    private static boolean isBeneath(String candidateIri, String parentIri) {
        return candidateIri.startsWith(stemOf(parentIri) + "/");
    }

    private static String stemOf(String ontologyIri) {
        return ontologyIri.endsWith(OWL_SUFFIX)
                ? ontologyIri.substring(0, ontologyIri.length() - OWL_SUFFIX.length())
                : ontologyIri;
    }
}
