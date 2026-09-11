package org.protege.editor.owl.model.declaration;

import org.semanticweb.owlapi.formats.FunctionalSyntaxDocumentFormat;
import org.semanticweb.owlapi.formats.OWLXMLDocumentFormat;
import org.semanticweb.owlapi.formats.PrefixDocumentFormat;
import org.semanticweb.owlapi.formats.RDFXMLDocumentFormat;
import org.semanticweb.owlapi.formats.TurtleDocumentFormat;
import org.semanticweb.owlapi.model.OWLDocumentFormat;

import javax.annotation.Nonnull;
import java.io.Serializable;
import java.util.Optional;
import java.util.function.BooleanSupplier;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Chooses the document format settings for a save.
 *
 * <p>For the built-in RDF/XML, Turtle, OWL/XML, and Functional Syntax formats, the format controls
 * whether saving adds declarations for entities that are used but not declared in the ontology or
 * its imports. When the user has chosen to leave those declarations out, this class returns a copy
 * with that option disabled. It copies the other save settings and leaves the ontology manager's
 * format unchanged.
 *
 * <p>If no change is needed, the original format is returned. The same is true for all other
 * format classes, including custom classes based on the four named formats.
 *
 * @author Josef Hardi
 */
public class SaveFormatResolver {

    /** The OWL API parameter for writing the {@code xsd:string} datatype on string literals. */
    static final String FORCE_XSD_STRING_PARAMETER = "force xsd:string on literals";

    @Nonnull
    private final BooleanSupplier suppressingAutomaticDeclarations;

    /**
     * Creates a resolver that reads the supplied setting on every call.
     *
     * @param suppressingAutomaticDeclarations returns {@code true} when automatic declarations
     *                                         should be left out
     */
    public SaveFormatResolver(@Nonnull BooleanSupplier suppressingAutomaticDeclarations) {
        this.suppressingAutomaticDeclarations = checkNotNull(suppressingAutomaticDeclarations);
    }

    /**
     * Returns the format to use when saving an ontology.
     *
     * @param format the current document format
     * @return a copy with automatic declarations disabled when the setting is switched on and the
     *         format supports it; otherwise, {@code format}
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
        OWLDocumentFormat copyFormat = copy.get();
        copySettings(format, copyFormat);
        copyFormat.setAddMissingTypes(false);
        return copyFormat;
    }
    
    @Nonnull
    private Optional<OWLDocumentFormat> copyOf(@Nonnull OWLDocumentFormat format) {
        if (format.getClass().equals(RDFXMLDocumentFormat.class)) {
            return Optional.of(new RDFXMLDocumentFormat());
        } else if (format.getClass().equals(TurtleDocumentFormat.class)) {
            return Optional.of(new TurtleDocumentFormat());
        } else if (format.getClass().equals(OWLXMLDocumentFormat.class)) {
            return Optional.of(new OWLXMLDocumentFormat());
        } else if (format.getClass().equals(FunctionalSyntaxDocumentFormat.class)) {
            return Optional.of(new FunctionalSyntaxDocumentFormat());
        } else {
            return Optional.empty();
        }
    }
    
    private void copySettings(@Nonnull OWLDocumentFormat source, @Nonnull OWLDocumentFormat target) {
        if (source.isPrefixOWLOntologyFormat() && target.isPrefixOWLOntologyFormat()) {
            PrefixDocumentFormat sourcePrefixes = source.asPrefixOWLOntologyFormat();
            PrefixDocumentFormat targetPrefixes = target.asPrefixOWLOntologyFormat();
            // copyPrefixesFrom also copies the default prefix, which is stored under ":".
            targetPrefixes.copyPrefixesFrom(sourcePrefixes);
            targetPrefixes.setPrefixComparator(sourcePrefixes.getPrefixComparator());
        }
        Serializable forceXsdString = source.getParameter(FORCE_XSD_STRING_PARAMETER, Boolean.FALSE);
        target.setParameter(FORCE_XSD_STRING_PARAMETER, forceXsdString);
        target.setOntologyLoaderMetaData(source.getOntologyLoaderMetaData());
    }
}
