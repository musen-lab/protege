package org.protege.editor.owl.model.declaration;

import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Verifies which entities are treated as standard vocabulary and excluded from findings.
 *
 * <p>The OWL API's built-in classification alone is insufficient because it depends on entity
 * type and omits some standard datatypes. {@link StandardVocabulary#contains(OWLEntity)} must also
 * recognise any entity in the RDF, RDFS, OWL, or XSD namespaces while leaving author-defined and
 * third-party vocabularies available to the declaration check.
 */
public class StandardVocabulary_TestCase {

    private static final OWLDataFactory DF = OWLManager.getOWLDataFactory();

    private static IRI iri(String s) {
        return IRI.create(s);
    }

    @Test
    public void shouldRecogniseOwlDefinedEntitiesAsStandard() {
        assertTrue(StandardVocabulary.contains(DF.getOWLThing()));
        assertTrue(StandardVocabulary.contains(DF.getOWLNothing()));
        assertTrue(StandardVocabulary.contains(
                DF.getOWLDatatype(iri("http://www.w3.org/2001/XMLSchema#integer"))));
        assertTrue(StandardVocabulary.contains(DF.getRDFSLabel()));
        assertTrue(StandardVocabulary.contains(
                DF.getOWLAnnotationProperty(iri("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"))));
    }

    @Test
    public void shouldRecogniseStandardDatatypesUnknownToTheOwlApi() {
        OWLDatatype date = DF.getOWLDatatype(iri("http://www.w3.org/2001/XMLSchema#date"));
        OWLDatatype duration = DF.getOWLDatatype(iri("http://www.w3.org/2001/XMLSchema#duration"));

        assertFalse("the first question alone answers no here", date.isBuiltIn());
        assertFalse("the first question alone answers no here", duration.isBuiltIn());

        assertTrue(StandardVocabulary.contains(date));
        assertTrue(StandardVocabulary.contains(duration));
    }

    @Test
    public void shouldRecogniseAStandardIriRegardlessOfEntityType() {
        IRI xsdInteger = iri("http://www.w3.org/2001/XMLSchema#integer");
        OWLAnnotationProperty asProperty = DF.getOWLAnnotationProperty(xsdInteger);

        assertFalse("the first question is answered per kind of term", asProperty.isBuiltIn());
        assertTrue(StandardVocabulary.contains(asProperty));
    }

    @Test
    public void shouldNotRecogniseTheAuthorsOwnTermsAsStandard() {
        assertFalse(StandardVocabulary.contains(DF.getOWLClass(iri("http://example.org/mine#A"))));
        assertFalse(StandardVocabulary.contains(
                DF.getOWLObjectProperty(iri("http://example.org/mine#p"))));
        assertFalse(StandardVocabulary.contains(
                DF.getOWLNamedIndividual(iri("http://example.org/mine#i"))));
    }

    @Test
    public void shouldNotRecogniseThirdPartyVocabulariesAsStandard() {
        // These do need declaring under OWL, so they must reach the check rather than
        // be filtered out here. Their severity is settled separately.
        assertFalse(StandardVocabulary.contains(
                DF.getOWLAnnotationProperty(iri("http://purl.org/dc/terms/title"))));
        assertFalse(StandardVocabulary.contains(
                DF.getOWLAnnotationProperty(iri("http://www.w3.org/2004/02/skos/core#prefLabel"))));
        assertFalse(StandardVocabulary.contains(DF.getOWLAnnotationProperty(
                iri("http://www.geneontology.org/formats/oboInOwl#hasOBONamespace"))));
        assertFalse(StandardVocabulary.contains(
                DF.getOWLAnnotationProperty(iri("http://xmlns.com/foaf/0.1/name"))));
    }
}
