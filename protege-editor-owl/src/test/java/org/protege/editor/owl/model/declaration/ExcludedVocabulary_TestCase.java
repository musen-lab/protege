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
 * <p>{@link OWLEntity#isBuiltIn()} is insufficient by itself: its result depends on the entity
 * type and it does not cover every term in the RDF, RDFS, OWL, and XSD namespaces. For example,
 * relying on it alone would report {@code xsd:date} whenever an ontology uses that datatype.
 * Combining it with {@link IRI#isReservedVocabulary()} excludes the standard vocabularies without
 * suppressing user-defined or third-party entities.
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
    public void shouldCoverThirdPartyVocabulariesWithNeitherGuard() {
        // These are legitimately undeclared in many real ontologies, and are
        // genuine OWL 2 DL violations when they are. Reporting them is a product
        // decision, not an accident: see the T4.3 PRD, open question 5.
        OWLAnnotationProperty dcTitle = DF.getOWLAnnotationProperty(iri("http://purl.org/dc/terms/title"));
        OWLAnnotationProperty skosPref =
                DF.getOWLAnnotationProperty(iri("http://www.w3.org/2004/02/skos/core#prefLabel"));
        OWLAnnotationProperty oboNamespace = DF.getOWLAnnotationProperty(
                iri("http://www.geneontology.org/formats/oboInOwl#hasOBONamespace"));
        OWLAnnotationProperty foafName = DF.getOWLAnnotationProperty(iri("http://xmlns.com/foaf/0.1/name"));

        for (OWLAnnotationProperty property : new OWLAnnotationProperty[] {
                dcTitle, skosPref, oboNamespace, foafName }) {
            assertFalse(property + " must not be excluded", isExcludedVocabulary(property));
        }
    }

    @Test
    public void shouldLetAnUndeclaredThirdPartyPropertyReachTheCheck() throws Exception {
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = m.getOWLDataFactory();
        OWLOntology o = m.createOntology(iri("http://example.org/vocab"));
        OWLClass c = df.getOWLClass(iri("http://example.org/vocab#C"));
        OWLAnnotationProperty dcTitle = df.getOWLAnnotationProperty(iri("http://purl.org/dc/terms/title"));
        m.addAxiom(o, df.getOWLDeclarationAxiom(c));
        m.addAxiom(o, df.getOWLAnnotationAssertionAxiom(dcTitle, c.getIRI(), df.getOWLLiteral("A")));

        assertTrue(o.getSignature(Imports.INCLUDED).contains(dcTitle));
        assertFalse(o.isDeclared(dcTitle, Imports.INCLUDED));
        assertFalse(isExcludedVocabulary(dcTitle));
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
