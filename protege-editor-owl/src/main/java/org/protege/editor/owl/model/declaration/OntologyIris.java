package org.protege.editor.owl.model.declaration;

import org.semanticweb.owlapi.model.OWLOntologyID;

import javax.annotation.Nonnull;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Utility methods for obtaining and comparing the primary IRI of an ontology.
 *
 * <p>An {@link OWLOntologyID} can contain an ontology IRI and a version IRI. The methods in this
 * class use only the ontology IRI. They expose it as a string because declaration ownership rules
 * compare its lexical form with entity namespaces; they do not otherwise parse or normalize it.
 *
 * @author Josef Hardi
 */
final class OntologyIris {

    private OntologyIris() {
    }

    /**
     * Returns the primary ontology IRI as a string.
     *
     * <p>The version IRI, if present, is ignored. An anonymous ontology identifier has no ontology
     * IRI, so this method returns an empty optional for it.
     *
     * @param ontology the ontology identifier from which to obtain the IRI
     * @return the string form of the ontology IRI, or an empty optional if {@code ontology} is
     *         anonymous
     * @throws NullPointerException if {@code ontology} is {@code null}
     */
    @Nonnull
    static Optional<String> ontologyIriOf(@Nonnull OWLOntologyID ontology) {
        checkNotNull(ontology);
        return ontology.isAnonymous()
                ? Optional.empty()
                : Optional.of(ontology.getOntologyIRI().get().toString());
    }

    /**
     * Removes at most one namespace separator from the end of an IRI string.
     *
     * <p>For example, both {@code http://example.org/base#} and
     * {@code http://example.org/base/} become {@code http://example.org/base}. All other content is
     * left unchanged; in particular, this method does not otherwise normalize the IRI.
     *
     * @param iri the IRI string to inspect
     * @return {@code iri} without one final {@code #} or {@code /}, or {@code iri} unchanged if it
     *         ends with neither character
     * @throws NullPointerException if {@code iri} is {@code null}
     */
    @Nonnull
    static String stripTrailingSeparator(@Nonnull String iri) {
        checkNotNull(iri);
        return iri.endsWith("#") || iri.endsWith("/") ? iri.substring(0, iri.length() - 1) : iri;
    }
}
