package org.protege.editor.owl.model.io.jsonld;

import com.fasterxml.jackson.databind.JsonNode;
import org.semanticweb.owlapi.formats.RDFJsonLDDocumentFormat;
import org.semanticweb.owlapi.io.StreamDocumentTarget;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.rio.RioJsonLDStorerFactory;
import org.semanticweb.owlapi.util.AbstractOWLStorer;

import javax.annotation.Nonnull;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Saves ontologies as JSON-LD using the settings in {@link JsonLdPreferences}.
 * <p>
 * Saving happens in two steps. First, OWL API's own JSON-LD writer writes the ontology to a temporary file
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
        try (TemporaryDraft draft = new TemporaryDraft()) {
            writeDraft(ontology, format, draft.path);
            Object expanded;
            try (InputStream input = Files.newInputStream(draft.path)) {
                expanded = JsonLdPostProcessor.rebuild(input, prefs);
            }
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
     * Step 1: OWL API's standard JSON-LD writer to disk, preserving addMissingTypes.
     */
    private static void writeDraft(@Nonnull OWLOntology ontology,
                                    @Nonnull OWLDocumentFormat format,
                                    @Nonnull Path path) throws OWLOntologyStorageException {
        RDFJsonLDDocumentFormat draftFormat = new RDFJsonLDDocumentFormat();
        draftFormat.setAddMissingTypes(format.isAddMissingTypes());
        try (OutputStream output = Files.newOutputStream(path)) {
            new RioJsonLDStorerFactory().createStorer()
                    .storeOntology(ontology, new StreamDocumentTarget(output), draftFormat);
        } catch (IOException e) {
            throw new OWLOntologyStorageException("Error writing the JSON-LD draft", e);
        }
    }

    /** Deletes the intermediate file on success or failure, preserving any original exception. */
    private static final class TemporaryDraft implements Closeable {

        private final Path path;

        TemporaryDraft() throws IOException {
            path = Files.createTempFile("protege-jsonld-", ".jsonld");
        }

        @Override
        public void close() throws IOException {
            Files.deleteIfExists(path);
        }
    }

}
