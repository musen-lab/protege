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
import java.util.Arrays;
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
 * Verifies how {@link SaveFormatResolver} prepares a format when automatic declarations are turned
 * off.
 *
 * <p>For RDF/XML, Turtle, OWL/XML, and Functional Syntax, the resolver returns a separate format
 * with automatic declarations disabled and the other save settings preserved. It never changes the
 * format owned by the ontology manager. Other format classes are returned unchanged, and the
 * user's setting is read again for every call.
 */
public class SaveFormatResolver_TestCase {

    private static final SaveFormatResolver SUPPRESSING_DECLARATIONS =
            new SaveFormatResolver(() -> true);

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
        for (OWLDocumentFormat format : allFormats()) {
            assertSame(format.getClass().getSimpleName(), format,
                    ADDING_DECLARATIONS.getSaveFormat(format));
        }
    }

    @Test
    public void shouldReturnACopyOfTheSameFormatClassWhenSuppressing() {
        for (OWLDocumentFormat format : supportedFormats()) {
            OWLDocumentFormat copy = SUPPRESSING_DECLARATIONS.getSaveFormat(format);
            assertThat(copy, not(sameInstance(format)));
            assertThat(copy, instanceOf(format.getClass()));
        }
    }

    @Test
    public void shouldDisableAddMissingTypesOnTheCopy() {
        for (OWLDocumentFormat format : supportedFormats()) {
            assertFalse(format.getClass().getSimpleName(),
                    SUPPRESSING_DECLARATIONS.getSaveFormat(format).isAddMissingTypes());
        }
    }

    @Test
    public void shouldNeverModifyTheGivenFormat() {
        for (OWLDocumentFormat format : allFormats()) {
            SUPPRESSING_DECLARATIONS.getSaveFormat(format);
            assertTrue("The original format was changed: " + format.getClass().getSimpleName(),
                    format.isAddMissingTypes());
        }
    }

    @Test
    public void shouldReturnOtherFormatsUnchangedWhenSuppressing() {
        for (OWLDocumentFormat format : otherFormats()) {
            assertSame(format.getClass().getSimpleName(), format,
                    SUPPRESSING_DECLARATIONS.getSaveFormat(format));
        }
    }

    @Test
    public void shouldReturnTheGivenFormatWhenItAlreadyAddsNoMissingTypes() {
        OWLDocumentFormat format = new TurtleDocumentFormat();
        format.setAddMissingTypes(false);
        assertSame(format, SUPPRESSING_DECLARATIONS.getSaveFormat(format));
    }

    @Test
    public void shouldReturnACustomTurtleFormatUnchanged() {
        OWLDocumentFormat format = new CustomTurtleDocumentFormat();
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

    /** The default prefix controls how relative IRIs are written, so it must be copied. */
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
        for (OWLDocumentFormat format : supportedFormats()) {
            format.setParameter(SaveFormatResolver.FORCE_XSD_STRING_PARAMETER, Boolean.TRUE);

            OWLDocumentFormat copy = SUPPRESSING_DECLARATIONS.getSaveFormat(format);
            assertThat(copy.getParameter(SaveFormatResolver.FORCE_XSD_STRING_PARAMETER,
                    Boolean.FALSE), is((Object) Boolean.TRUE));
        }
    }

    @Test
    public void shouldLeaveTheForceXsdStringParameterUnsetWhenTheSourceHasNone() {
        for (OWLDocumentFormat format : supportedFormats()) {
            OWLDocumentFormat copy = SUPPRESSING_DECLARATIONS.getSaveFormat(format);
            assertThat(copy.getParameter(SaveFormatResolver.FORCE_XSD_STRING_PARAMETER,
                    Boolean.FALSE), is((Object) Boolean.FALSE));
        }
    }

    @Test
    public void shouldCopyTheOntologyLoaderMetaData() {
        OWLOntologyLoaderMetaData metaData = new RDFParserMetaData();
        for (OWLDocumentFormat format : supportedFormats()) {
            format.setOntologyLoaderMetaData(metaData);

            OWLDocumentFormat copy = SUPPRESSING_DECLARATIONS.getSaveFormat(format);
            assertThat(format.getClass().getSimpleName(),
                    copy.getOntologyLoaderMetaData(), is(sameInstance(metaData)));
        }
    }

    /** A setting change must take effect on the next save without a restart. */
    @Test
    public void shouldReadThePreferenceOnEveryCall() {
        boolean[] suppressing = {false};
        SaveFormatResolver resolver = new SaveFormatResolver(() -> suppressing[0]);
        OWLDocumentFormat format = new TurtleDocumentFormat();

        assertSame(format, resolver.getSaveFormat(format));
        suppressing[0] = true;
        assertThat(resolver.getSaveFormat(format), not(sameInstance(format)));
    }

    private static OWLDocumentFormat[] supportedFormats() {
        return new OWLDocumentFormat[]{
                new RDFXMLDocumentFormat(),
                new TurtleDocumentFormat(),
                new OWLXMLDocumentFormat(),
                new FunctionalSyntaxDocumentFormat()
        };
    }

    private static OWLDocumentFormat[] otherFormats() {
        return new OWLDocumentFormat[]{
                new ManchesterSyntaxDocumentFormat(),
                new OBODocumentFormat(),
                new LatexDocumentFormat(),
                new RDFJsonLDDocumentFormat(),
                new RioTurtleDocumentFormat()
        };
    }

    private static List<OWLDocumentFormat> allFormats() {
        List<OWLDocumentFormat> formats = new ArrayList<>();
        formats.addAll(Arrays.asList(supportedFormats()));
        formats.addAll(Arrays.asList(otherFormats()));
        return formats;
    }

    private static List<PrefixDocumentFormat> prefixFormats() {
        List<PrefixDocumentFormat> prefixFormats = new ArrayList<>();
        for (OWLDocumentFormat format : supportedFormats()) {
            if (format.isPrefixOWLOntologyFormat()) {
                prefixFormats.add(format.asPrefixOWLOntologyFormat());
            }
        }
        return prefixFormats;
    }

    private static class CustomTurtleDocumentFormat extends TurtleDocumentFormat {
    }
}
