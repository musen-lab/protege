package org.protege.editor.owl.model.declaration;

import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;

/**
 * Verifies how missing declarations are assigned a severity.
 *
 * <p>{@link DeclarationSeverity#of(OWLEntity)} considers the entity type and nothing else. A named
 * individual is a warning, because OWL 2 does not require its declaration; every other entity type
 * is an error.
 */
public class DeclarationSeverity_TestCase {

    private static final OWLDataFactory DF = OWLManager.getOWLDataFactory();

    private static final String OWN = "http://example.org/mine#";
    private static final String DUBLIN_CORE = "http://purl.org/dc/terms/";
    private static final String SKOS = "http://www.w3.org/2004/02/skos/core#";
    private static final String SCHEMA_ORG = "http://schema.org/";
    private static final String OBO = "http://purl.obolibrary.org/obo/";

    /** Entity factories used to exercise every entity type against the same namespaces. */
    private static final List<Function<IRI, OWLEntity>> BUILDERS = Arrays.asList(
            DF::getOWLClass,
            DF::getOWLDatatype,
            DF::getOWLObjectProperty,
            DF::getOWLDataProperty,
            DF::getOWLAnnotationProperty,
            DF::getOWLNamedIndividual);

    private static DeclarationSeverity expectedFor(OWLEntity entity) {
        return entity.getEntityType() == EntityType.NAMED_INDIVIDUAL
                ? DeclarationSeverity.WARNING
                : DeclarationSeverity.ERROR;
    }

    private void assertSeverityDependsOnlyOnEntityType(String namespace) {
        for (Function<IRI, OWLEntity> builder : BUILDERS) {
            OWLEntity entity = builder.apply(IRI.create(namespace + "Term"));
            assertEquals(namespace + " " + entity.getEntityType().getName(),
                    expectedFor(entity), DeclarationSeverity.of(entity));
        }
    }

    @Test
    public void shouldTreatEveryEntityTypeInTheAuthorsOwnVocabularyByTypeAlone() {
        assertSeverityDependsOnlyOnEntityType(OWN);
    }

    @Test
    public void shouldTreatEveryEntityTypeInAnExternalVocabularyByTypeAlone() {
        assertSeverityDependsOnlyOnEntityType(DUBLIN_CORE);
        assertSeverityDependsOnlyOnEntityType(SKOS);
        assertSeverityDependsOnlyOnEntityType(SCHEMA_ORG);
    }

    @Test
    public void shouldAssignTheSameSeverityToTheSameEntityTypeInAnyNamespace() {
        OWLAnnotationProperty mine = DF.getOWLAnnotationProperty(IRI.create(OWN + "note"));
        OWLAnnotationProperty dublinCore = DF.getOWLAnnotationProperty(IRI.create(DUBLIN_CORE + "title"));
        OWLAnnotationProperty skos = DF.getOWLAnnotationProperty(IRI.create(SKOS + "prefLabel"));

        assertEquals(DeclarationSeverity.ERROR, DeclarationSeverity.of(mine));
        assertEquals(DeclarationSeverity.ERROR, DeclarationSeverity.of(dublinCore));
        assertEquals(DeclarationSeverity.ERROR, DeclarationSeverity.of(skos));
    }

    @Test
    public void shouldTreatAnIndividualAsAWarningInAnyNamespace() {
        assertEquals(DeclarationSeverity.WARNING,
                DeclarationSeverity.of(DF.getOWLNamedIndividual(IRI.create(OWN + "i"))));
        assertEquals(DeclarationSeverity.WARNING,
                DeclarationSeverity.of(DF.getOWLNamedIndividual(IRI.create(SKOS + "i"))));
    }

    @Test
    public void shouldTreatAnOboTermAsAnError() {
        // Every OBO ontology puts its terms in this one namespace, so no namespace rule could tell
        // an OBO term the author owns from one they borrow.
        OWLClass goTerm = DF.getOWLClass(IRI.create(OBO + "GO_0008150"));

        assertEquals(DeclarationSeverity.ERROR, DeclarationSeverity.of(goTerm));
    }
}
