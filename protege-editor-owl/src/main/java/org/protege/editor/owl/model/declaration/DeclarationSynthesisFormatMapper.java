package org.protege.editor.owl.model.declaration;

import org.semanticweb.owlapi.formats.PrefixDocumentFormat;
import org.semanticweb.owlapi.formats.RDFXMLDocumentFormat;
import org.semanticweb.owlapi.formats.TurtleDocumentFormat;
import org.semanticweb.owlapi.model.OWLDocumentFormat;

import javax.annotation.Nonnull;
import java.io.Serializable;
import java.util.function.BooleanSupplier;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Produces the document format an ontology should be written with, given the user's choice about
 * where entity declarations belong.
 *
 * <p>When the user has asked to keep declarations with the ontology that defines the entity, and the
 * format is one this capability governs, the caller gets a <em>copy</em> with
 * {@code addMissingTypes} cleared.  The format passed in is never modified: the instance the ontology
 * manager holds must keep saying what it said when the ontology was loaded, or every plugin that
 * reads it afterwards would be told something false about the ontology on disk.
 *
 * <p>Flipping the flag and putting it back would be observable rather than merely untidy.  Saving
 * runs on a background thread while the progress dialog holds the event dispatch thread, so anything
 * that reads the format during a save would see the flipped value.
 *
 * <p>The flag arrives as a {@link BooleanSupplier} rather than being read here, so that tests do not
 * have to touch the user's stored preferences.  It is supplied in the same sense the checkbox reads
 * and the preference stores it, so no step between the dialog and the save inverts it.
 *
 * @author Josef Hardi
 */
public class DeclarationSynthesisFormatMapper {

    /**
     * The one format parameter the RDF/XML and Turtle renderers read
     * ({@code RDFXMLRenderer} and {@code TurtleRenderer} both consult it for literal typing).
     *
     * <p>A format's parameter map is private with no way to enumerate it, so a copy cannot be
     * exhaustive.  Copying this key by name keeps the only value that currently matters, and leaves
     * a compile-time trace for whoever adds the next one.
     */
    static final String FORCE_XSD_STRING_PARAMETER = "force xsd:string on literals";

    @Nonnull
    private final BooleanSupplier keepDeclarationsInDefiningOntology;

    public DeclarationSynthesisFormatMapper(@Nonnull BooleanSupplier keepDeclarationsInDefiningOntology) {
        this.keepDeclarationsInDefiningOntology = checkNotNull(keepDeclarationsInDefiningOntology);
    }

    /**
     * @param format the format the ontology manager holds for the ontology being saved
     * @return a copy that writes declarations only for the entities the ontology defines, when the
     *         user has asked for that and the format is one this capability governs; otherwise
     *         {@code format} itself, unchanged
     */
    @Nonnull
    public OWLDocumentFormat mapFormat(@Nonnull OWLDocumentFormat format) {
        checkNotNull(format);
        if (keepDeclarationsInDefiningOntology.getAsBoolean()
                && DeclarationSynthesisFormats.isGoverned(format)) {
            OWLDocumentFormat mappedFormat = copyOf(format);
            mappedFormat.setAddMissingTypes(false);
            return mappedFormat;
        }
        return format;
    }

    @Nonnull
    private OWLDocumentFormat copyOf(@Nonnull OWLDocumentFormat format) {
        OWLDocumentFormat copy = newFormatLike(format);
        if (format.isPrefixOWLOntologyFormat() && copy.isPrefixOWLOntologyFormat()) {
            PrefixDocumentFormat source = format.asPrefixOWLOntologyFormat();
            PrefixDocumentFormat target = copy.asPrefixOWLOntologyFormat();
            // Carries every prefix, the default prefix among them: it lives in the map under ":".
            target.copyPrefixesFrom(source);
            target.setPrefixComparator(source.getPrefixComparator());
        }
        Serializable forceXsdString = format.getParameter(FORCE_XSD_STRING_PARAMETER, Boolean.FALSE);
        copy.setParameter(FORCE_XSD_STRING_PARAMETER, forceXsdString);
        return copy;
    }

    @Nonnull
    private OWLDocumentFormat newFormatLike(@Nonnull OWLDocumentFormat format) {
        if (format instanceof RDFXMLDocumentFormat) {
            return new RDFXMLDocumentFormat();
        }
        if (format instanceof TurtleDocumentFormat) {
            return new TurtleDocumentFormat();
        }
        throw new IllegalStateException(
                "No copy is defined for the governed format " + format.getClass().getName()
                        + ".  Add one here when adding it to DeclarationSynthesisFormats.");
    }
}
