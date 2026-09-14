package org.protege.editor.owl.model.declaration;

import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.Imports;
import org.semanticweb.owlapi.vocab.OWL2Datatype;

import static org.junit.Assert.*;

/**
 * Verifies the vocabulary exclusions required by a missing-declaration check.
 *
 * <p>The builtin vocabularies (such as RDF, RDFS, OWL, and XSD namespaces) are the only
 * entities the check never reports. Everything else an ontology uses must be declared somewhere
 * in its import closure.
 */
public class ExcludedVocabulary_TestCase {

    private static final OWLDataFactory DF = OWLManager.getOWLDataFactory();

    private static IRI iri(String s) {
        return IRI.create(s);
    }

    /**
     * Tests the combined guard also used by the OWL API profile checker.
     *
     * @param entity the entity to classify
     * @return {@code true} if the entity is built in or has a reserved-vocabulary IRI
     */
    private static boolean isExcludedVocabulary(OWLEntity entity) {
        return entity.isBuiltIn() || entity.getIRI().isReservedVocabulary();
    }

    @Test
    public void shouldAnswerBuiltInPerEntityTypeForTheSameIri() {
        IRI xsdInteger = iri("http://www.w3.org/2001/XMLSchema#integer");

        assertTrue("built in when asked as a datatype",
                DF.getOWLDatatype(xsdInteger).isBuiltIn());
        assertFalse("not built in when asked as an annotation property",
                DF.getOWLAnnotationProperty(xsdInteger).isBuiltIn());
    }

    @Test
    public void shouldMissDatatypesOutsideTheOwl2DatatypeMap() {
        OWLDatatype xsdDate = DF.getOWLDatatype(iri("http://www.w3.org/2001/XMLSchema#date"));
        OWLDatatype xsdDuration = DF.getOWLDatatype(iri("http://www.w3.org/2001/XMLSchema#duration"));

        assertFalse(xsdDate.isBuiltIn());
        assertFalse(xsdDuration.isBuiltIn());
        assertFalse("xsd:date is genuinely outside the OWL 2 datatype map",
                OWL2Datatype.isBuiltIn(xsdDate.getIRI()));

        // But the check must still not report them.
        assertTrue(isExcludedVocabulary(xsdDate));
        assertTrue(isExcludedVocabulary(xsdDuration));
    }

    @Test
    public void shouldCoverTheRdfRdfsOwlAndXsdNamespacesAsReservedVocabulary() {
        assertTrue(iri("http://www.w3.org/2001/XMLSchema#date").isReservedVocabulary());
        assertTrue(iri("http://www.w3.org/2000/01/rdf-schema#label").isReservedVocabulary());
        assertTrue(iri("http://www.w3.org/2002/07/owl#deprecated").isReservedVocabulary());
        assertTrue(iri("http://www.w3.org/1999/02/22-rdf-syntax-ns#type").isReservedVocabulary());
    }

    @Test
    public void shouldLetAnUndeclaredExternalVocabularyPropertyReachTheCheckAsAnError() throws Exception {
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = m.getOWLDataFactory();
        OWLOntology o = m.createOntology(iri("http://example.org/vocab"));
        OWLClass c = df.getOWLClass(iri("http://example.org/vocab#C"));
        OWLAnnotationProperty dcTitle = df.getOWLAnnotationProperty(iri("http://purl.org/dc/terms/title"));
        m.addAxiom(o, df.getOWLDeclarationAxiom(c));
        m.addAxiom(o, df.getOWLAnnotationAssertionAxiom(dcTitle, c.getIRI(), df.getOWLLiteral("A")));

        assertTrue(o.getSignature(Imports.INCLUDED).contains(dcTitle));
        assertFalse(o.isDeclared(dcTitle, Imports.INCLUDED));
        assertFalse("an external vocabulary is not standard vocabulary",
                isExcludedVocabulary(dcTitle));
        assertEquals(DeclarationSeverity.ERROR, DeclarationSeverity.of(dcTitle));
    }

    @Test
    public void shouldNeverExcludeAUserDefinedEntity() throws Exception {
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = m.getOWLDataFactory();
        OWLClass a = df.getOWLClass(iri("http://example.org/mine#A"));
        OWLObjectProperty p = df.getOWLObjectProperty(iri("http://example.org/mine#p"));
        OWLNamedIndividual i = df.getOWLNamedIndividual(iri("http://example.org/mine#i"));

        assertFalse(isExcludedVocabulary(a));
        assertFalse(isExcludedVocabulary(p));
        assertFalse(isExcludedVocabulary(i));
    }
}
