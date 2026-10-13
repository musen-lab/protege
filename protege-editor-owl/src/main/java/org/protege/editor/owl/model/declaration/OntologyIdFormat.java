package org.protege.editor.owl.model.declaration;

import org.semanticweb.owlapi.model.OWLOntologyID;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Produces human-readable ontology identifiers for a reporting display.
 *
 * @author Josef Hardi
 */
public final class OntologyIdFormat {

    private OntologyIdFormat() {
    }

    /**
     * Formats an ontology identifier for display in a declaration report.
     *
     * @param ontologyId the ontology identifier to format
     * @return the ontology IRI for a named ontology, followed by
     *         {@code (version <versionIri>)} when a version IRI is present; otherwise the complete
     *         identifier string for an anonymous ontology
     * @throws NullPointerException if {@code ontologyId} is {@code null}
     */
    @Nonnull
    public static String format(@Nonnull OWLOntologyID ontologyId) {
        checkNotNull(ontologyId);
        if (ontologyId.isAnonymous()) {
            return ontologyId.toString();
        }
        String ontologyIri = ontologyId.getOntologyIRI().get().toString();
        return ontologyId.getVersionIRI().isPresent()
                ? ontologyIri + " (version " + ontologyId.getVersionIRI().get() + ")"
                : ontologyIri;
    }

    /**
     * Formats a collection of ontology identifiers for display in a declaration report.
     *
     * @param ontologyIds the ontology identifiers to format
     * @return the {@linkplain #format(OWLOntologyID) formatted} identifiers in iteration order,
     *         separated by {@code ", "}
     * @throws NullPointerException if {@code ontologyIds} or any of its elements is {@code null}
     */
    @Nonnull
    public static String formatAll(@Nonnull Collection<OWLOntologyID> ontologyIds) {
        checkNotNull(ontologyIds);
        return ontologyIds.stream()
                .map(OntologyIdFormat::format)
                .collect(Collectors.joining(", "));
    }
}
