package org.protege.editor.owl.model.declaration;

import org.junit.Test;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLOntologyID;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Verifies which ontologies count as one family of published documents.
 *
 * <p>The relationship is read from the ontology identifiers alone. A component document beneath a
 * project's path is family; a sibling project whose name merely starts the same way is not.
 */
public class DocumentFamily_TestCase {

    private static final String OBO = "http://purl.obolibrary.org/obo/";

    @Test
    public void shouldTreatAnOntologyAsItsOwnFamily() {
        OWLOntologyID go = id(OBO + "go.owl");

        assertTrue(DocumentFamily.sameFamily(go, go));
    }

    @Test
    public void shouldTreatAComponentAsFamilyOfItsProject() {
        assertTrue(DocumentFamily.sameFamily(id(OBO + "go/components/x.owl"), id(OBO + "go.owl")));
    }

    /** The relationship holds whichever of the two documents owns the namespace. */
    @Test
    public void shouldReadTheRelationshipInBothDirections() {
        assertTrue(DocumentFamily.sameFamily(id(OBO + "go.owl"), id(OBO + "go/components/x.owl")));
    }

    @Test
    public void shouldNotTreatASiblingProjectAsFamily() {
        // The stem comparison appends a slash precisely so that goa.owl stays out.
        assertFalse(DocumentFamily.sameFamily(id(OBO + "goa.owl"), id(OBO + "go.owl")));
        assertFalse(DocumentFamily.sameFamily(id(OBO + "go.owl"), id(OBO + "goa.owl")));
    }

    @Test
    public void shouldNotTreatAModuleOfAnotherProjectAsFamily() {
        assertFalse(DocumentFamily.sameFamily(
                id(OBO + "cl/imports/go_import.owl"), id(OBO + "go.owl")));
    }

    @Test
    public void shouldGiveAnAnonymousOntologyNoFamilyButItself() {
        OWLOntologyID anonymous = new OWLOntologyID();
        OWLOntologyID go = id(OBO + "go.owl");

        assertTrue(DocumentFamily.sameFamily(anonymous, anonymous));
        assertFalse(DocumentFamily.sameFamily(anonymous, go));
        assertFalse(DocumentFamily.sameFamily(go, anonymous));
    }

    private static OWLOntologyID id(String iri) {
        return new OWLOntologyID(com.google.common.base.Optional.of(IRI.create(iri)),
                com.google.common.base.Optional.absent());
    }
}
