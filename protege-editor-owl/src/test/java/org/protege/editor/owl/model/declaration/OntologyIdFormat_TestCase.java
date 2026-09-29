package org.protege.editor.owl.model.declaration;

import org.junit.Test;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLOntologyID;

import static org.junit.Assert.*;

/**
 * Tests how {@link OntologyIdFormat} identifies an ontology in a report.
 */
public class OntologyIdFormat_TestCase {

    @Test
    public void shouldShowANamedOntologyByItsIri() {
        OWLOntologyID id = new OWLOntologyID(IRI.create("http://example.org/pizza"));

        assertEquals("http://example.org/pizza", OntologyIdFormat.format(id));
    }

    @Test
    public void shouldFollowTheOntologyIriWithTheVersionIri() {
        OWLOntologyID id = new OWLOntologyID(IRI.create("http://example.org/pizza"),
                IRI.create("http://example.org/pizza/1.0"));

        assertEquals("http://example.org/pizza (version http://example.org/pizza/1.0)",
                OntologyIdFormat.format(id));
    }

    @Test
    public void shouldTellTwoAnonymousOntologiesApart() {
        String first = OntologyIdFormat.format(new OWLOntologyID());
        String second = OntologyIdFormat.format(new OWLOntologyID());

        assertFalse(first.isEmpty());
        assertNotEquals(first, second);
    }
}
