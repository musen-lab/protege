package org.protege.editor.owl.model.declaration;

import org.junit.Test;
import org.semanticweb.owlapi.formats.FunctionalSyntaxDocumentFormat;
import org.semanticweb.owlapi.formats.PrefixDocumentFormat;
import org.semanticweb.owlapi.formats.RDFXMLDocumentFormat;
import org.semanticweb.owlapi.formats.RioTurtleDocumentFormat;
import org.semanticweb.owlapi.formats.TurtleDocumentFormat;
import org.semanticweb.owlapi.model.OWLDocumentFormat;
import org.semanticweb.owlapi.util.StringComparator;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class DeclarationSynthesisFormatMapper_TestCase {

    /** The user asked to keep declarations with the ontology that defines the entity. */
    private static final DeclarationSynthesisFormatMapper KEEP_IN_DEFINING_ONTOLOGY =
            new DeclarationSynthesisFormatMapper(() -> true);

    /** The default: Protege declares every entity it finds referenced. */
    private static final DeclarationSynthesisFormatMapper DECLARE_EVERYTHING =
            new DeclarationSynthesisFormatMapper(() -> false);

    @Test(expected = NullPointerException.class)
    public void shouldThrowNullPointerException() {
        KEEP_IN_DEFINING_ONTOLOGY.mapFormat(null);
    }

    @Test(expected = NullPointerException.class)
    public void shouldThrowNullPointerExceptionForANullSupplier() {
        new DeclarationSynthesisFormatMapper(null);
    }

    @Test
    public void shouldPassThroughGovernedFormatsWhenTheSettingIsOff() {
        OWLDocumentFormat rdfXml = new RDFXMLDocumentFormat();
        OWLDocumentFormat turtle = new TurtleDocumentFormat();
        assertSame(rdfXml, DECLARE_EVERYTHING.mapFormat(rdfXml));
        assertSame(turtle, DECLARE_EVERYTHING.mapFormat(turtle));
    }

    @Test
    public void shouldPassThroughUngovernedFormatsWhenTheSettingIsOn() {
        OWLDocumentFormat functional = new FunctionalSyntaxDocumentFormat();
        OWLDocumentFormat rioTurtle = new RioTurtleDocumentFormat();
        assertSame(functional, KEEP_IN_DEFINING_ONTOLOGY.mapFormat(functional));
        assertSame(rioTurtle, KEEP_IN_DEFINING_ONTOLOGY.mapFormat(rioTurtle));
    }

    @Test
    public void shouldReturnADifferentInstanceOfTheSameClass() {
        for (OWLDocumentFormat format : governedFormats()) {
            OWLDocumentFormat mapped = KEEP_IN_DEFINING_ONTOLOGY.mapFormat(format);
            assertThat(mapped, not(sameInstance(format)));
            assertThat(mapped, instanceOf(format.getClass()));
        }
    }

    @Test
    public void shouldClearAddMissingTypesOnTheCopy() {
        for (OWLDocumentFormat format : governedFormats()) {
            assertFalse(format.getClass().getSimpleName(),
                    KEEP_IN_DEFINING_ONTOLOGY.mapFormat(format).isAddMissingTypes());
        }
    }

    @Test
    public void shouldLeaveTheSourceFormatUntouched() {
        for (OWLDocumentFormat format : governedFormats()) {
            KEEP_IN_DEFINING_ONTOLOGY.mapFormat(format);
            assertTrue("mapping mutated the format it was given: " + format.getClass().getSimpleName(),
                    format.isAddMissingTypes());
        }
    }

    @Test
    public void shouldCopyPrefixes() {
        for (OWLDocumentFormat format : governedFormats()) {
            PrefixDocumentFormat source = format.asPrefixOWLOntologyFormat();
            source.setPrefix("ex:", "http://example.invalid/ex#");

            PrefixDocumentFormat mapped = KEEP_IN_DEFINING_ONTOLOGY.mapFormat(format).asPrefixOWLOntologyFormat();
            assertThat(mapped.getPrefix("ex:"), is("http://example.invalid/ex#"));
        }
    }

    /**
     * The Turtle renderer writes the default prefix as the document base, so losing it would change
     * every relative IRI in the output.
     */
    @Test
    public void shouldCopyTheDefaultPrefix() {
        for (OWLDocumentFormat format : governedFormats()) {
            PrefixDocumentFormat source = format.asPrefixOWLOntologyFormat();
            source.setDefaultPrefix("http://example.invalid/default#");

            PrefixDocumentFormat mapped = KEEP_IN_DEFINING_ONTOLOGY.mapFormat(format).asPrefixOWLOntologyFormat();
            assertThat(mapped.getDefaultPrefix(), is("http://example.invalid/default#"));
        }
    }

    @Test
    public void shouldCopyThePrefixComparator() {
        StringComparator reverseOrder = (left, right) -> right.compareTo(left);
        for (OWLDocumentFormat format : governedFormats()) {
            PrefixDocumentFormat source = format.asPrefixOWLOntologyFormat();
            source.setPrefixComparator(reverseOrder);

            PrefixDocumentFormat mapped = KEEP_IN_DEFINING_ONTOLOGY.mapFormat(format).asPrefixOWLOntologyFormat();
            assertThat(mapped.getPrefixComparator(), is(sameInstance(reverseOrder)));
        }
    }

    @Test
    public void shouldCopyTheForceXsdStringParameter() {
        for (OWLDocumentFormat format : governedFormats()) {
            format.setParameter(DeclarationSynthesisFormatMapper.FORCE_XSD_STRING_PARAMETER, Boolean.TRUE);

            OWLDocumentFormat mapped = KEEP_IN_DEFINING_ONTOLOGY.mapFormat(format);
            assertThat(mapped.getParameter(DeclarationSynthesisFormatMapper.FORCE_XSD_STRING_PARAMETER,
                    Boolean.FALSE), is((Object) Boolean.TRUE));
        }
    }

    @Test
    public void shouldNotInventTheForceXsdStringParameter() {
        for (OWLDocumentFormat format : governedFormats()) {
            OWLDocumentFormat mapped = KEEP_IN_DEFINING_ONTOLOGY.mapFormat(format);
            assertThat(mapped.getParameter(DeclarationSynthesisFormatMapper.FORCE_XSD_STRING_PARAMETER,
                    Boolean.FALSE), is((Object) Boolean.FALSE));
        }
    }

    /**
     * The setting is read on every call, so a change reaches the next save without a restart.
     */
    @Test
    public void shouldReadTheSettingOnEveryCall() {
        boolean[] keepInDefiningOntology = {false};
        DeclarationSynthesisFormatMapper mapper =
                new DeclarationSynthesisFormatMapper(() -> keepInDefiningOntology[0]);
        OWLDocumentFormat format = new TurtleDocumentFormat();

        assertSame(format, mapper.mapFormat(format));
        keepInDefiningOntology[0] = true;
        assertThat(mapper.mapFormat(format), not(sameInstance(format)));
    }

    private static OWLDocumentFormat[] governedFormats() {
        return new OWLDocumentFormat[]{new RDFXMLDocumentFormat(), new TurtleDocumentFormat()};
    }
}
