/**
 * Configurable JSON-LD serialization for Protégé ontologies.
 *
 * <p>{@link org.protege.editor.owl.model.io.jsonld.ConfigurableJsonLdStorer} first asks OWL API to
 * write JSON-LD, then uses {@link org.protege.editor.owl.model.io.jsonld.JsonLdPostProcessor} to
 * apply the context and output settings held in
 * {@link org.protege.editor.owl.model.io.jsonld.JsonLdPreferences}. The prefix-aware document
 * format carries mappings to the context builder. When a local JSON-LD file is reopened,
 * {@link org.protege.editor.owl.model.io.jsonld.JsonLdPrefixReader} restores its mappings so a
 * later save can use them again.
 */
package org.protege.editor.owl.model.io.jsonld;
