package org.protege.editor.owl.model.declaration;

import com.google.common.collect.ImmutableSet;
import org.semanticweb.owlapi.formats.RDFXMLDocumentFormat;
import org.semanticweb.owlapi.formats.TurtleDocumentFormat;
import org.semanticweb.owlapi.model.OWLDocumentFormat;

import javax.annotation.Nonnull;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * The document formats whose declaration synthesis Protégé governs.
 *
 * <p>This is the one place the set is defined.  Anything that needs to know whether a format is
 * governed asks here.
 *
 * <p>Membership is by exact class, not by {@code isInstance}.  That is deliberate:
 * {@code RioTurtleDocumentFormat} does not extend {@code TurtleDocumentFormat} - it descends from
 * {@code RioRDFPrefixDocumentFormat} - so a subtype test would not catch it anyway, and
 * {@link org.protege.editor.owl.model.DocumentFormatUpdater} already rewrites Rio Turtle to
 * {@code TurtleDocumentFormat} when an ontology loads.  A narrow match also keeps an unrelated
 * future format from becoming governed by accident.
 *
 * @author Josef Hardi
 */
public class DeclarationSynthesisFormats {

    /**
     * The governed formats.  These are also the only two RDF formats the Save As dialog offers.
     */
    public static final ImmutableSet<Class<? extends OWLDocumentFormat>> GOVERNED_FORMATS =
            ImmutableSet.of(RDFXMLDocumentFormat.class, TurtleDocumentFormat.class);

    public static boolean isGoverned(@Nonnull OWLDocumentFormat format) {
        checkNotNull(format);
        return GOVERNED_FORMATS.contains(format.getClass());
    }

    private DeclarationSynthesisFormats() {
    }
}
