package org.protege.editor.owl.model.declaration;

import org.junit.Test;
import org.semanticweb.owlapi.formats.FunctionalSyntaxDocumentFormat;
import org.semanticweb.owlapi.formats.ManchesterSyntaxDocumentFormat;
import org.semanticweb.owlapi.formats.OWLXMLDocumentFormat;
import org.semanticweb.owlapi.formats.RDFJsonLDDocumentFormat;
import org.semanticweb.owlapi.formats.RDFXMLDocumentFormat;
import org.semanticweb.owlapi.formats.RioTurtleDocumentFormat;
import org.semanticweb.owlapi.formats.TurtleDocumentFormat;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DeclarationSynthesisFormats_TestCase {

    @Test
    public void shouldGovernRdfXml() {
        assertTrue(DeclarationSynthesisFormats.isGoverned(new RDFXMLDocumentFormat()));
    }

    @Test
    public void shouldGovernTurtle() {
        assertTrue(DeclarationSynthesisFormats.isGoverned(new TurtleDocumentFormat()));
    }

    /**
     * Rio Turtle is rewritten to {@code TurtleDocumentFormat} when the ontology loads, so it never
     * reaches a save.  It is not a {@code TurtleDocumentFormat} subtype either.
     */
    @Test
    public void shouldNotGovernRioTurtle() {
        assertFalse(DeclarationSynthesisFormats.isGoverned(new RioTurtleDocumentFormat()));
    }

    @Test
    public void shouldNotGovernOwlXml() {
        assertFalse(DeclarationSynthesisFormats.isGoverned(new OWLXMLDocumentFormat()));
    }

    @Test
    public void shouldNotGovernFunctionalSyntax() {
        assertFalse(DeclarationSynthesisFormats.isGoverned(new FunctionalSyntaxDocumentFormat()));
    }

    @Test
    public void shouldNotGovernOtherFormatsTheSaveAsDialogOffers() {
        assertFalse(DeclarationSynthesisFormats.isGoverned(new ManchesterSyntaxDocumentFormat()));
        assertFalse(DeclarationSynthesisFormats.isGoverned(new RDFJsonLDDocumentFormat()));
    }

    @Test
    public void shouldDefineExactlyTwoGovernedFormats() {
        assertThat(DeclarationSynthesisFormats.GOVERNED_FORMATS, hasSize(2));
    }

    @Test(expected = NullPointerException.class)
    public void shouldThrowNullPointerException() {
        DeclarationSynthesisFormats.isGoverned(null);
    }
}
