package org.protege.editor.owl.model.declaration;

import org.junit.Test;
import org.semanticweb.owlapi.formats.FunctionalSyntaxDocumentFormat;
import org.semanticweb.owlapi.formats.LatexDocumentFormat;
import org.semanticweb.owlapi.formats.ManchesterSyntaxDocumentFormat;
import org.semanticweb.owlapi.formats.OBODocumentFormat;
import org.semanticweb.owlapi.formats.OWLXMLDocumentFormat;
import org.semanticweb.owlapi.formats.PrefixDocumentFormat;
import org.semanticweb.owlapi.formats.RDFJsonLDDocumentFormat;
import org.semanticweb.owlapi.formats.RDFXMLDocumentFormat;
import org.semanticweb.owlapi.formats.RioTurtleDocumentFormat;
import org.semanticweb.owlapi.formats.TurtleDocumentFormat;
import org.semanticweb.owlapi.io.OWLOntologyLoaderMetaData;
import org.semanticweb.owlapi.io.RDFParserMetaData;
import org.semanticweb.owlapi.model.OWLDocumentFormat;
import org.semanticweb.owlapi.util.StringComparator;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The save format resolver, over every kind of document format, with the preference both ways.
 *
 * <p>Pins that a format comes back as a copy of its own class, that the format handed in is never
 * modified, and that the preference is read on every call rather than cached.
 */
public class SaveFormatResolver_TestCase {

    /** The user asked to suppress the declarations Protege adds when saving. */
    private static final SaveFormatResolver SUPPRESSING_DECLARATIONS =
            new SaveFormatResolver(() -> true);

    /** The default: Protege declares every entity it finds referenced. */
    private static final SaveFormatResolver ADDING_DECLARATIONS =
            new SaveFormatResolver(() -> false);

    @Test(expected = NullPointerException.class)
    public void shouldRejectANullFormat() {
        SUPPRESSING_DECLARATIONS.getSaveFormat(null);
    }

    @Test(expected = NullPointerException.class)
    public void shouldRejectANullPreference() {
        new SaveFormatResolver(null);
    }

    @Test
    public void shouldReturnTheGivenFormatWhenNotSuppressing() {
        for (OWLDocumentFormat format : formats()) {
            assertSame(format.getClass().getSimpleName(), format,
                    ADDING_DECLARATIONS.getSaveFormat(format));
        }
    }

    @Test
    public void shouldReturnACopyOfTheSameFormatClassWhenSuppressing() {
        for (OWLDocumentFormat format : formats()) {
            OWLDocumentFormat copy = SUPPRESSING_DECLARATIONS.getSaveFormat(format);
            assertThat(copy, not(sameInstance(format)));
            assertThat(copy, instanceOf(format.getClass()));
        }
    }

    @Test
    public void shouldDisableAddMissingTypesOnTheCopy() {
        for (OWLDocumentFormat format : formats()) {
            assertFalse(format.getClass().getSimpleName(),
                    SUPPRESSING_DECLARATIONS.getSaveFormat(format).isAddMissingTypes());
        }
    }

    @Test
    public void shouldNeverModifyTheGivenFormat() {
        for (OWLDocumentFormat format : formats()) {
            SUPPRESSING_DECLARATIONS.getSaveFormat(format);
            assertTrue("mapping mutated the format it was given: " + format.getClass().getSimpleName(),
                    format.isAddMissingTypes());
        }
    }

    /**
     * A format that already adds no missing type needs no copy.
     */
    @Test
    public void shouldReturnTheGivenFormatWhenItAlreadyAddsNoMissingTypes() {
        OWLDocumentFormat format = new TurtleDocumentFormat();
        format.setAddMissingTypes(false);
        assertSame(format, SUPPRESSING_DECLARATIONS.getSaveFormat(format));
    }

    /**
     * A format the resolver cannot copy is returned as it is, and the save adds the declarations
     * Protege has always added.
     */
    @Test
    public void shouldReturnTheGivenFormatWhenItCannotBeCopied() {
        OWLDocumentFormat format = new FormatWithoutANoArgConstructor("turtle");
        assertSame(format, SUPPRESSING_DECLARATIONS.getSaveFormat(format));
    }

    @Test
    public void shouldCopyThePrefixes() {
        for (PrefixDocumentFormat source : prefixFormats()) {
            source.setPrefix("ex:", "http://example.invalid/ex#");

            PrefixDocumentFormat copy = SUPPRESSING_DECLARATIONS.getSaveFormat(source)
                    .asPrefixOWLOntologyFormat();
            assertThat(copy.getPrefix("ex:"), is("http://example.invalid/ex#"));
        }
    }

    /**
     * The Turtle renderer writes the default prefix as the document base, so losing it would change
     * every relative IRI in the output.
     */
    @Test
    public void shouldCopyTheDefaultPrefix() {
        for (PrefixDocumentFormat source : prefixFormats()) {
            source.setDefaultPrefix("http://example.invalid/default#");

            PrefixDocumentFormat copy = SUPPRESSING_DECLARATIONS.getSaveFormat(source)
                    .asPrefixOWLOntologyFormat();
            assertThat(copy.getDefaultPrefix(), is("http://example.invalid/default#"));
        }
    }

    @Test
    public void shouldCopyThePrefixComparator() {
        StringComparator reverseOrder = (left, right) -> right.compareTo(left);
        for (PrefixDocumentFormat source : prefixFormats()) {
            source.setPrefixComparator(reverseOrder);

            PrefixDocumentFormat copy = SUPPRESSING_DECLARATIONS.getSaveFormat(source)
                    .asPrefixOWLOntologyFormat();
            assertThat(copy.getPrefixComparator(), is(sameInstance(reverseOrder)));
        }
    }

    @Test
    public void shouldCopyTheForceXsdStringParameter() {
        for (OWLDocumentFormat format : formats()) {
            format.setParameter(SaveFormatResolver.FORCE_XSD_STRING_PARAMETER, Boolean.TRUE);

            OWLDocumentFormat copy = SUPPRESSING_DECLARATIONS.getSaveFormat(format);
            assertThat(copy.getParameter(SaveFormatResolver.FORCE_XSD_STRING_PARAMETER,
                    Boolean.FALSE), is((Object) Boolean.TRUE));
        }
    }

    @Test
    public void shouldLeaveTheForceXsdStringParameterUnsetWhenTheSourceHasNone() {
        for (OWLDocumentFormat format : formats()) {
            OWLDocumentFormat copy = SUPPRESSING_DECLARATIONS.getSaveFormat(format);
            assertThat(copy.getParameter(SaveFormatResolver.FORCE_XSD_STRING_PARAMETER,
                    Boolean.FALSE), is((Object) Boolean.FALSE));
        }
    }

    @Test
    public void shouldCopyTheOntologyLoaderMetaData() {
        OWLOntologyLoaderMetaData metaData = new RDFParserMetaData();
        for (OWLDocumentFormat format : formats()) {
            format.setOntologyLoaderMetaData(metaData);

            OWLDocumentFormat copy = SUPPRESSING_DECLARATIONS.getSaveFormat(format);
            assertThat(format.getClass().getSimpleName(),
                    copy.getOntologyLoaderMetaData(), is(sameInstance(metaData)));
        }
    }

    /**
     * The preference is read on every call, so a change reaches the next save without a restart.
     */
    @Test
    public void shouldReadThePreferenceOnEveryCall() {
        boolean[] suppressing = {false};
        SaveFormatResolver resolver = new SaveFormatResolver(() -> suppressing[0]);
        OWLDocumentFormat format = new TurtleDocumentFormat();

        assertSame(format, resolver.getSaveFormat(format));
        suppressing[0] = true;
        assertThat(resolver.getSaveFormat(format), not(sameInstance(format)));
    }

    /**
     * A fresh format of every kind the Save As dialog offers, plus two the ontology manager can hold
     * after a load: prefix and non-prefix, RDF and syntax-rendered, native and Rio-backed.
     */
    private static OWLDocumentFormat[] formats() {
        return new OWLDocumentFormat[]{
                new RDFXMLDocumentFormat(),
                new TurtleDocumentFormat(),
                new OWLXMLDocumentFormat(),
                new FunctionalSyntaxDocumentFormat(),
                new ManchesterSyntaxDocumentFormat(),
                new OBODocumentFormat(),
                new LatexDocumentFormat(),
                new RDFJsonLDDocumentFormat(),
                new RioTurtleDocumentFormat()
        };
    }

    private static List<PrefixDocumentFormat> prefixFormats() {
        List<PrefixDocumentFormat> prefixFormats = new ArrayList<>();
        for (OWLDocumentFormat format : formats()) {
            if (format.isPrefixOWLOntologyFormat()) {
                prefixFormats.add(format.asPrefixOWLOntologyFormat());
            }
        }
        return prefixFormats;
    }

    /** A format the resolver has no way to build, standing in for one a plugin might supply. */
    private static class FormatWithoutANoArgConstructor extends TurtleDocumentFormat {

        FormatWithoutANoArgConstructor(String unused) {
        }
    }
}
