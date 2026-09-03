package org.protege.editor.owl.model.library.folder;

import java.io.File;
import java.net.URI;
import java.util.Collections;
import java.util.Set;

/**
 * An {@link Algorithm} that can tell declared IRIs from derived ones.
 * <p>
 * A <em>primary</em> suggestion is an IRI the document itself states (an
 * {@code owl:Ontology} declaration, a version IRI, an {@code xml:base}). A
 * <em>secondary</em> suggestion is derived by convention rather than read from
 * the document, such as the OBO Foundry purls computed for an OBO file. When two
 * files claim the same IRI, {@link FolderGroupManager} lets a primary claim win
 * over secondary ones instead of recording a duplicate, so the OWL rendering of
 * an ontology keeps resolving when its OBO rendering sits in the same folder.
 * <p>
 * Package-private on purpose: this refines the folder scan without widening the
 * public {@code Algorithm} plugin API. Implementations must produce both sets
 * from a single read of the file.
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
