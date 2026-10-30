package org.protege.editor.owl.model.io.jsonld;

import org.semanticweb.owlapi.model.OWLStorer;
import org.semanticweb.owlapi.util.OWLStorerFactoryImpl;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * OWL API storer factory for configurable JSON-LD output. Production storers read the
 * application preferences when created; tests may supply a separate settings instance.
 */
public class ConfigurableJsonLdStorerFactory extends OWLStorerFactoryImpl {

    private static final long serialVersionUID = 1L;

    @Nullable
    private final transient JsonLdPreferences prefs;

    /** Storers from this factory use Protégé's global JSON-LD settings. */
    public ConfigurableJsonLdStorerFactory() {
        this(null);
    }

    /**
     * Creates a factory backed by the supplied settings, or by the global settings if they are absent.
     *
     * @param prefs settings for storers from this factory, or {@code null} for the application settings
     */
    public ConfigurableJsonLdStorerFactory(@Nullable JsonLdPreferences prefs) {
        super(new ConfigurableJsonLDDocumentFormatFactory());
        this.prefs = prefs;
    }

    @Nonnull
    @Override
    public OWLStorer createStorer() {
        return new ConfigurableJsonLdStorer(prefs != null ? prefs : JsonLdPreferences.getInstance());
    }
}
