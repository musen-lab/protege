package org.protege.editor.owl.model.io.jsonld;

import com.fasterxml.jackson.databind.JsonNode;
import org.semanticweb.owlapi.formats.RDFJsonLDDocumentFormat;
import org.semanticweb.owlapi.io.StreamDocumentTarget;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.rio.RioJsonLDStorerFactory;
import org.semanticweb.owlapi.util.AbstractOWLStorer;

import javax.annotation.Nonnull;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Saves ontologies as JSON-LD using the settings in {@link JsonLdPreferences}.
 * <p>
 * Saving happens in two steps. First, OWL API's own JSON-LD writer writes the ontology into memory
 * (full IRIs, no context), preserving the supplied format's add-missing-types setting.
 * Then {@link JsonLdPostProcessor} rebuilds that document with
 * jsonld-java, and shapes it with the {@code @context} from {@link JsonLdContextBuilder} and the other settings.
 */
public class ConfigurableJsonLdStorer extends AbstractOWLStorer {

    private static final long serialVersionUID = 1L;

    private final JsonLdPreferences prefs;

    /**
     * Creates a storer that reads the supplied settings when an ontology is saved.
     *
     * @param prefs the JSON-LD settings to apply
     */
    public ConfigurableJsonLdStorer(@Nonnull JsonLdPreferences prefs) {
        this.prefs = Objects.requireNonNull(prefs);
    }

    @Override
    public boolean canStoreOntology(@Nonnull OWLDocumentFormat format) {
        return format instanceof RDFJsonLDDocumentFormat;
    }

    @Override
    protected void storeOntology(@Nonnull OWLOntology ontology,
                                 @Nonnull Writer writer,
                                 @Nonnull OWLDocumentFormat format) throws OWLOntologyStorageException {
        try {
            JsonLdPostProcessor.write(buildDocument(ontology, format), prefs, writer);
        } catch (IOException e) {
            throw new OWLOntologyStorageException("Error writing JSON-LD", e);
        }
    }

    @Override
    protected void storeOntology(@Nonnull OWLOntology ontology,
                                 @Nonnull OutputStream os,
                                 @Nonnull OWLDocumentFormat format) throws OWLOntologyStorageException {
        try {
            JsonLdPostProcessor.write(buildDocument(ontology, format), prefs, os);
        } catch (IOException e) {
            throw new OWLOntologyStorageException("Error writing JSON-LD", e);
        }
    }

    private JsonNode buildDocument(@Nonnull OWLOntology ontology,
                                   @Nonnull OWLDocumentFormat format) throws OWLOntologyStorageException {
        byte[] draft = writeDraft(ontology, format);
        try {
            Object expanded = JsonLdPostProcessor.rebuild(draft, prefs);
            // Expanded files have no @context, so none is built (and a name clash can't stop the save)
            JsonLdContextBuilder contextBuilder = new JsonLdContextBuilder(prefs);
            Map<String, Object> context = prefs.getDocumentForm() == DocumentForm.EXPANDED
                    ? Collections.emptyMap()
                    : contextBuilder.build(ontology, format, JsonLdPostProcessor.usedIris(expanded));
            return JsonLdPostProcessor.shapeAndFinish(expanded, context, contextBuilder.getPrefixNames(), prefs);
        } catch (IOException e) {
            throw new OWLOntologyStorageException("Error processing JSON-LD", e);
        }
    }

    /**
     * Step 1: OWL API's standard JSON-LD writer into memory, preserving addMissingTypes.
     */
    private static byte[] writeDraft(@Nonnull OWLOntology ontology,
                                     @Nonnull OWLDocumentFormat format) throws OWLOntologyStorageException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        RDFJsonLDDocumentFormat draftFormat = new RDFJsonLDDocumentFormat();
        draftFormat.setAddMissingTypes(format.isAddMissingTypes());
        try {
            new RioJsonLDStorerFactory().createStorer()
                    .storeOntology(ontology, new StreamDocumentTarget(buffer), draftFormat);
        } catch (IOException e) {
            throw new OWLOntologyStorageException("Error writing the JSON-LD draft", e);
        }
        return buffer.toByteArray();
    }

}
