package org.protege.editor.owl.model.declaration;

import org.semanticweb.owlapi.formats.PrefixDocumentFormat;
import org.semanticweb.owlapi.model.OWLDocumentFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.Serializable;
import java.util.Optional;
import java.util.function.BooleanSupplier;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Resolves the document format to use when saving.
 *
 * <p>If the user has chosen to keep declarations in their respective ontologies, this method returns
 * a <em>copy</em> of the document format with {@code addMissingTypes} disabled. Otherwise, it returns
 * the original document format instance unchanged. The provided document format is never modified, so
 * the ontology manager's instance continues to represent the format as it was loaded.
 *
 * @author Josef Hardi
 */
public class SaveFormatResolver {

    private static final Logger logger = LoggerFactory.getLogger(SaveFormatResolver.class);

    /**
     * The one format parameter these renderers read, and so the one a copy must carry over.
     */
    static final String FORCE_XSD_STRING_PARAMETER = "force xsd:string on literals";

    @Nonnull
    private final BooleanSupplier suppressingAutomaticDeclarations;

    /**
     * Creates a provider over the user's declaration setting.
     *
     * @param suppressingAutomaticDeclarations supplies the setting, read afresh on every call
     */
    public SaveFormatResolver(@Nonnull BooleanSupplier suppressingAutomaticDeclarations) {
        this.suppressingAutomaticDeclarations = checkNotNull(suppressingAutomaticDeclarations);
    }

    /**
     * Returns the document format to use when saving the ontology.
     *
     * @param format the document format provided by the ontology manager
     * @return {@code format} unchanged if automatic declarations are not being suppressed; otherwise
     * a copy of {@code format} with automatic entity declarations disabled.
     */
    @Nonnull
    public OWLDocumentFormat getSaveFormat(@Nonnull OWLDocumentFormat format) {
        checkNotNull(format);
        boolean suppressed = suppressingAutomaticDeclarations.getAsBoolean();
        if (!suppressed || !format.isAddMissingTypes()) {
            return format;
        }
        Optional<OWLDocumentFormat> copy = copyOf(format);
        if (copy.isEmpty()) {
            return format;
        }
        OWLDocumentFormat saveFormat = copy.get();
        copySettings(format, saveFormat);
        saveFormat.setAddMissingTypes(false); // declare only what the ontology states.
        return saveFormat;
    }
    
    @Nonnull
    private Optional<OWLDocumentFormat> copyOf(@Nonnull OWLDocumentFormat format) {
        try {
            return Optional.of(format.getClass().getDeclaredConstructor().newInstance());
        } catch (ReflectiveOperationException | RuntimeException e) {
            String formatName = format.getClass().getName();
            logger.warn("Cannot copy the {} document format: {}", formatName, e.getMessage());
            return Optional.empty();
        }
    }
    
    private void copySettings(@Nonnull OWLDocumentFormat source, @Nonnull OWLDocumentFormat target) {
        if (source.isPrefixOWLOntologyFormat() && target.isPrefixOWLOntologyFormat()) {
            PrefixDocumentFormat sourcePrefixes = source.asPrefixOWLOntologyFormat();
            PrefixDocumentFormat targetPrefixes = target.asPrefixOWLOntologyFormat();
            // Carries every prefix, the default prefix among them: it lives in the map under ":".
            targetPrefixes.copyPrefixesFrom(sourcePrefixes);
            targetPrefixes.setPrefixComparator(sourcePrefixes.getPrefixComparator());
        }
        Serializable forceXsdString = source.getParameter(FORCE_XSD_STRING_PARAMETER, Boolean.FALSE);
        target.setParameter(FORCE_XSD_STRING_PARAMETER, forceXsdString);
        target.setOntologyLoaderMetaData(source.getOntologyLoaderMetaData());
    }
}
