package org.protege.editor.owl.model.library.folder;

import java.io.File;
import java.net.URI;
import java.util.Collections;
import java.util.Set;

/**
 * An {@link Algorithm} that keeps the IRIs it read from a file apart from the
 * IRIs it built by convention.
 * <p>
 * A <em>primary</em> IRI is one the file states itself: an {@code owl:Ontology}
 * declaration, a version IRI, an {@code xml:base}. A <em>secondary</em> IRI is
 * worked out from other information, such as the OBO Foundry address built from
 * an OBO file's {@code ontology:} line. When two files give the same IRI,
 * {@link FolderGroupManager} lets a primary one win over a secondary one, so that
 * {@code x.owl} still resolves when {@code x.obo} sits next to it.
 * <p>
 * Implementations must return both sets from a single read of the file.
 */
interface PrioritizedAlgorithm extends Algorithm {

    enum Priority {
        PRIMARY, SECONDARY
    }

    final class Suggestions {

        static final Suggestions NONE = new Suggestions(Collections.emptySet(), Collections.emptySet());

        final Set<URI> primary;

        final Set<URI> secondary;

        Suggestions(Set<URI> primary, Set<URI> secondary) {
            this.primary = primary;
            this.secondary = secondary;
        }

        boolean isEmpty() {
            return primary.isEmpty() && secondary.isEmpty();
        }
    }

    Suggestions getPrioritizedSuggestions(File f);
}
