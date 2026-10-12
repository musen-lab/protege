package org.protege.editor.owl.model.io.jsonld;

import org.semanticweb.owlapi.model.OWLStorer;
import org.semanticweb.owlapi.util.OWLStorerFactoryImpl;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Factory for creating ConfigurableJsonLdStorer instances that use Protégé's JSON-LD settings.
 */
public class ConfigurableJsonLdStorerFactory extends OWLStorerFactoryImpl {

    private static final long serialVersionUID = 1L;

    @Nullable
    private final transient JsonLdPreferences prefs;

    /** Storers from this factory use Protégé's global JSON-LD settings. */
    public ConfigurableJsonLdStorerFactory() {
        this(null);
    }

    /** Storers from this factory use the given settings (for example in tests); null means the global settings. */
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
