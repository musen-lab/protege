package org.protege.editor.owl.model.io.jsonld;

import org.semanticweb.owlapi.formats.PrefixDocumentFormat;
import org.semanticweb.owlapi.formats.RDFJsonLDDocumentFormat;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.PrefixManager;
import org.semanticweb.owlapi.util.DefaultPrefixManager;
import org.semanticweb.owlapi.util.StringComparator;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;

/**
 * JSON-LD document format that carries prefix mappings alongside OWL API's RDF JSON-LD format.
 * OWL API's base format does not expose prefixes; implementing {@link PrefixDocumentFormat}
 * lets Protégé pass ontology mappings to the storer and keep mappings recovered when a file is reopened.
 * {@link ConfigurableJsonLdStorer} uses those mappings to build the JSON-LD {@code @context}.
 * Other serialization settings are stored in {@link JsonLdPreferences}.
 */
public class ConfigurableJsonLDDocumentFormat extends RDFJsonLDDocumentFormat implements PrefixDocumentFormat {

    private static final long serialVersionUID = 1L;

    private final DefaultPrefixManager prefixManager;

    /** Creates a JSON-LD format with no prefix mappings. */
    public ConfigurableJsonLDDocumentFormat() {
        super();
        this.prefixManager = new DefaultPrefixManager();
    }

    /**
     * Creates a JSON-LD format with a copy of the supplied prefix mappings.
     * Later changes to the supplied manager do not change this format.
     *
     * @param prefixManager the source of the prefix mappings
     */
    public ConfigurableJsonLDDocumentFormat(@Nonnull PrefixManager prefixManager) {
        super();
        this.prefixManager = new DefaultPrefixManager();
        this.prefixManager.copyPrefixesFrom(prefixManager);
    }

    // --- PrefixDocumentFormat and PrefixManager delegations ---

    @Override
    public boolean isPrefixOWLOntologyFormat() {
        return true;
    }

    @Override
    public PrefixDocumentFormat asPrefixOWLOntologyFormat() {
        return this;
    }

    /** Replaces this format's mappings with a copy from the supplied manager. */
    @Override
    public void setPrefixManager(@Nonnull PrefixManager pm) {
        this.prefixManager.clear();
        this.prefixManager.copyPrefixesFrom(pm);
    }

    @Override
    public void setPrefix(@Nonnull String prefixName, @Nonnull String prefix) {
        prefixManager.setPrefix(prefixName, prefix);
    }

    @Override
    public void clear() {
        prefixManager.clear();
    }

    @Nonnull
    @Override
    public Set<String> getPrefixNames() {
        return prefixManager.getPrefixNames();
    }

    @Override
    public void setDefaultPrefix(@Nullable String defaultPrefix) {
        prefixManager.setDefaultPrefix(defaultPrefix);
    }

    @Override
    public boolean containsPrefixMapping(@Nonnull String prefixName) {
        return prefixManager.containsPrefixMapping(prefixName);
    }

    @Nullable
    @Override
    public String getDefaultPrefix() {
        return prefixManager.getDefaultPrefix();
    }

    @Nonnull
    @Override
    public Map<String, String> getPrefixName2PrefixMap() {
        return prefixManager.getPrefixName2PrefixMap();
    }

    @Nullable
    @Override
    public String getPrefix(@Nonnull String prefixName) {
        return prefixManager.getPrefix(prefixName);
    }

    @Nonnull
    @Override
    public IRI getIRI(@Nonnull String prefixIRI) {
        return prefixManager.getIRI(prefixIRI);
    }

    @Nullable
    @Override
    public String getPrefixIRI(@Nonnull IRI iri) {
        return prefixManager.getPrefixIRI(iri);
    }

    @Nullable
    @Override
    public String getPrefixIRIIgnoreQName(@Nonnull IRI iri) {
        return prefixManager.getPrefixIRIIgnoreQName(iri);
    }

    @Override
    public void copyPrefixesFrom(@Nonnull PrefixManager from) {
        prefixManager.copyPrefixesFrom(from);
    }

    @Override
    public void copyPrefixesFrom(@Nonnull Map<String, String> from) {
        prefixManager.copyPrefixesFrom(from);
    }

    @Override
    public void unregisterNamespace(@Nonnull String namespace) {
        prefixManager.unregisterNamespace(namespace);
    }

    @Nonnull
    @Override
    public StringComparator getPrefixComparator() {
        return prefixManager.getPrefixComparator();
    }

    @Override
    public void setPrefixComparator(@Nonnull StringComparator comparator) {
        prefixManager.setPrefixComparator(comparator);
    }
}
