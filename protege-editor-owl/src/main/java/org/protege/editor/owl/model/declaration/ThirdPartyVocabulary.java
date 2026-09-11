package org.protege.editor.owl.model.declaration;

import org.semanticweb.owlapi.model.OWLEntity;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Provides a set of recognized third-party vocabulary namespaces, such as Dublin Core and SKOS.
 *
 * <p>Entities from these namespaces are defined and maintained by external vocabulary providers.
 * A missing declaration for such an entity is therefore treated as a warning rather than an error,
 * since the declaration is not expected to be maintained by the ontology author.
 *
 * <p>The recognized vocabularies are identified by a fixed set of namespaces. Entities whose
 * namespaces are not recognized are treated as belonging to the ontology being edited.
 *
 * @author Josef Hardi
 */
public final class ThirdPartyVocabulary {

    private static final Set<String> NAMESPACES = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList(
                    "http://purl.org/dc/elements/1.1/",                 // Dublin Core Elements
                    "http://purl.org/dc/terms/",                        // Dublin Core Terms
                    "http://www.w3.org/2004/02/skos/core#",             // SKOS
                    "http://www.w3.org/2006/vcard/ns#",                 // vCard
                    "http://www.geneontology.org/formats/oboInOwl#",    // OBO-in_OWL
                    "http://xmlns.com/foaf/0.1/",                       // FOAF
                    "http://www.w3.org/ns/",                            // W3C vocabularies
                    "http://schema.org/",                               // Schema.org (HTTP)
                    "https://schema.org/")));                           // Schema.org (HTTPS)

    private ThirdPartyVocabulary() {
    }

    /**
     * Gets the namespaces of the recognized third-party vocabularies.
     *
     * @return the recognized namespaces, in their defined order
     */
    @Nonnull
    public static Collection<String> getNamespaces() {
        return NAMESPACES;
    }

    /**
     * Determines whether the specified entity belongs to a recognized third-party vocabulary.
     *
     * @param entity the entity to check
     * @return {@code true} if the entity's namespace belongs to a recognized third-party
     *         vocabulary, otherwise {@code false}
     */
    public static boolean contains(@Nonnull OWLEntity entity) {
        checkNotNull(entity);
        return NAMESPACES.contains(entity.getIRI().getNamespace());
    }
}
