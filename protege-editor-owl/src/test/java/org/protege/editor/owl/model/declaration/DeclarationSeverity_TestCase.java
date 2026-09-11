package org.protege.editor.owl.model.declaration;

import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Verifies how missing declarations are assigned a severity.
 *
 * <p>{@link DeclarationSeverity#of(OWLEntity)} considers both the entity type and the vocabulary
 * namespace. Entities from the ontology author's vocabulary are errors except for named
 * individuals, while entities from recognised third-party vocabularies are warnings regardless of
 * type. Namespace recognition must be exact and must not classify the shared OBO namespace as
 * third-party vocabulary.
 */
public class DeclarationSeverity_TestCase {

    private static final OWLDataFactory DF = OWLManager.getOWLDataFactory();

    private static final String OWN = "http://example.org/mine#";
    private static final String THIRD_PARTY = "http://purl.org/dc/terms/";

    /** Entity factories used to exercise every entity type against the same namespaces. */
    private static final List<Function<IRI, OWLEntity>> BUILDERS = Arrays.asList(
            DF::getOWLClass,
            DF::getOWLDatatype,
            DF::getOWLObjectProperty,
            DF::getOWLDataProperty,
            DF::getOWLAnnotationProperty,
            DF::getOWLNamedIndividual);

    @Test
    public void shouldTreatOwnVocabularyAsAnErrorExceptForIndividuals() {
        for (Function<IRI, OWLEntity> builder : BUILDERS) {
            OWLEntity entity = builder.apply(IRI.create(OWN + "Term"));
            DeclarationSeverity expected = entity.getEntityType() == EntityType.NAMED_INDIVIDUAL
                    ? DeclarationSeverity.WARNING
                    : DeclarationSeverity.ERROR;
            assertEquals(entity.getEntityType().getName(), expected, DeclarationSeverity.of(entity));
        }
    }

    @Test
    public void shouldTreatThirdPartyVocabularyAsAWarningForEveryEntityType() {
        for (Function<IRI, OWLEntity> builder : BUILDERS) {
            OWLEntity entity = builder.apply(IRI.create(THIRD_PARTY + "term"));
            assertEquals(entity.getEntityType().getName(),
                    DeclarationSeverity.WARNING, DeclarationSeverity.of(entity));
        }
    }

    @Test
    public void shouldAssignDifferentSeveritiesToTheSameEntityTypeByNamespace() {
        OWLAnnotationProperty mine = DF.getOWLAnnotationProperty(IRI.create(OWN + "note"));
        OWLAnnotationProperty theirs = DF.getOWLAnnotationProperty(IRI.create(THIRD_PARTY + "title"));

        assertEquals(DeclarationSeverity.ERROR, DeclarationSeverity.of(mine));
        assertEquals(DeclarationSeverity.WARNING, DeclarationSeverity.of(theirs));
    }

    @Test
    public void shouldRecogniseEveryListedThirdPartyNamespace() {
        for (String namespace : ThirdPartyVocabulary.getNamespaces()) {
            OWLAnnotationProperty property = DF.getOWLAnnotationProperty(IRI.create(namespace + "x"));
            assertTrue(namespace, ThirdPartyVocabulary.contains(property));
        }
        assertFalse(ThirdPartyVocabulary.getNamespaces().isEmpty());
    }

    @Test
    public void shouldIncludeTheNamedThirdPartyVocabularies() {
        assertTrue(ThirdPartyVocabulary.contains(
                DF.getOWLAnnotationProperty(IRI.create("http://purl.org/dc/terms/title"))));
        assertTrue(ThirdPartyVocabulary.contains(
                DF.getOWLAnnotationProperty(IRI.create("http://purl.org/dc/elements/1.1/title"))));
        assertTrue(ThirdPartyVocabulary.contains(
                DF.getOWLAnnotationProperty(IRI.create("http://www.w3.org/2004/02/skos/core#prefLabel"))));
        assertTrue(ThirdPartyVocabulary.contains(DF.getOWLAnnotationProperty(
                IRI.create("http://www.geneontology.org/formats/oboInOwl#hasOBONamespace"))));
        assertTrue(ThirdPartyVocabulary.contains(
                DF.getOWLAnnotationProperty(IRI.create("http://xmlns.com/foaf/0.1/name"))));
    }

    @Test
    public void shouldTreatAnUnlistedVocabularyAsTheAuthorsOwn() {
        assertFalse(ThirdPartyVocabulary.contains(
                DF.getOWLAnnotationProperty(IRI.create("http://purl.org/vocab/vann/preferredNamespacePrefix"))));
        assertFalse(ThirdPartyVocabulary.contains(
                DF.getOWLClass(IRI.create("http://example.org/mine#A"))));
    }

    @Test
    public void shouldMatchTheWholeNamespaceRatherThanAPrefix() {
        // Listing "http://www.w3.org/ns/" covers a term sitting directly under that path.
        assertTrue(ThirdPartyVocabulary.contains(
                DF.getOWLAnnotationProperty(IRI.create("http://www.w3.org/ns/example"))));

        // It does not reach the vocabularies published one level below it, because each of those
        // ends at its own separator. Listing them means listing them by name.
        assertFalse(ThirdPartyVocabulary.contains(
                DF.getOWLClass(IRI.create("http://www.w3.org/ns/prov#Entity"))));
        assertFalse(ThirdPartyVocabulary.contains(
                DF.getOWLClass(IRI.create("http://www.w3.org/ns/dcat#Dataset"))));
    }

    @Test
    public void shouldRecogniseBothHttpAndHttpsSchemaOrgNamespaces() {
        assertTrue(ThirdPartyVocabulary.contains(
                DF.getOWLAnnotationProperty(IRI.create("http://schema.org/name"))));
        assertTrue(ThirdPartyVocabulary.contains(
                DF.getOWLAnnotationProperty(IRI.create("https://schema.org/name"))));
    }

    @Test
    public void shouldNotTreatTheSharedOboNamespaceAsThirdParty() {
        // Every OBO ontology puts its terms in this one namespace, so treating it as third-party
        // would demote every OBO term in every OBO ontology to a warning.
        OWLClass goTerm = DF.getOWLClass(IRI.create("http://purl.obolibrary.org/obo/GO_0008150"));

        assertFalse(ThirdPartyVocabulary.contains(goTerm));
        assertEquals(DeclarationSeverity.ERROR, DeclarationSeverity.of(goTerm));
    }
}
