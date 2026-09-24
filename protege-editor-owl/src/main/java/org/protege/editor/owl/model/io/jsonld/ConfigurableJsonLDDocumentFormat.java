package org.protege.editor.owl.model.io.jsonld;

import org.eclipse.rdf4j.rio.helpers.BasicWriterSettings;
import org.eclipse.rdf4j.rio.helpers.JSONLDSettings;
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
 * Prefix-aware, configurable JSON-LD document format.
 * Extends RDFJsonLDDocumentFormat and implements PrefixDocumentFormat so Protégé
 * and OWL API's RioRenderer preserve and forward ontology prefixes into the JSON-LD @context.
 */
public class ConfigurableJsonLDDocumentFormat extends RDFJsonLDDocumentFormat implements PrefixDocumentFormat {

    private static final long serialVersionUID = 1L;

    private final DefaultPrefixManager prefixManager;

    public ConfigurableJsonLDDocumentFormat() {
        super();
        this.prefixManager = new DefaultPrefixManager();
        applyPreferences(JsonLdPreferences.getInstance());
    }

    public ConfigurableJsonLDDocumentFormat(@Nonnull PrefixManager prefixManager) {
        super();
        this.prefixManager = new DefaultPrefixManager();
        this.prefixManager.copyPrefixesFrom(prefixManager);
        applyPreferences(JsonLdPreferences.getInstance());
    }

    /**
     * Applies settings from JsonLdPreferences into the format's parameters for RioStorer.
     */
    public void applyPreferences(@Nonnull JsonLdPreferences prefs) {
        setParameter(JSONLDSettings.JSONLD_MODE, prefs.getDocumentForm().getRioMode());
        setParameter(JSONLDSettings.COMPACT_ARRAYS, prefs.isCompactArrays());
        setParameter(JSONLDSettings.USE_NATIVE_TYPES, prefs.isUseNativeTypes());
        setParameter(BasicWriterSettings.PRETTY_PRINT, prefs.isPrettyPrint());
        setParameter(BasicWriterSettings.INLINE_BLANK_NODES, prefs.getBlankNodeEmbedding() != BlankNodeEmbedding.REFERENCE);
        setParameter(BasicWriterSettings.XSD_STRING_TO_PLAIN_LITERAL, prefs.isOmitXsdStringTypes());
        setParameter(JSONLDSettings.USE_RDF_TYPE, prefs.isUseRdfType());
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
