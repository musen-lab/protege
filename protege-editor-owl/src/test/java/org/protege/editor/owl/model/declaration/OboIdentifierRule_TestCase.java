package org.protege.editor.owl.model.declaration;

import org.junit.Before;
import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyID;
import org.semanticweb.owlapi.model.OWLOntologyManager;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * Tests how an OBO ID space is matched to the ontology that owns it.
 */
public class OboIdentifierRule_TestCase {

    private static final String OBO = "http://purl.obolibrary.org/obo/";

    private OWLOntologyManager manager;

    private OWLDataFactory dataFactory;

    private OboIdentifierRule rule;

    @Before
    public void setUp() {
        manager = OWLManager.createOWLOntologyManager();
        dataFactory = manager.getOWLDataFactory();
        rule = new OboIdentifierRule();
    }

    @Test
    public void shouldKeepTheIdentifierItsSettingIsStoredUnder() {
        assertEquals("misplaced.rule.use.obo.identifier", rule.getId());
    }

    @Test
    public void shouldResolveAnOboIdSpaceToItsOntology() throws Exception {
        OWLOntology go = ontology(OBO + "go.owl");

        assertOwner(go, clazz(OBO + "GO_0006915"), go);
    }

    @Test
    public void shouldResolveAnIdSpaceContainingAnUnderscore() throws Exception {
        OWLOntology apolloSv = ontology(OBO + "apollo_sv.owl");

        assertOwner(apolloSv, clazz(OBO + "APOLLO_SV_0000001"), apolloSv);
    }

    @Test
    public void shouldResolveAMixedCaseIdSpace() throws Exception {
        OWLOntology ncbiTaxon = ontology(OBO + "ncbitaxon.owl");

        assertOwner(ncbiTaxon, clazz(OBO + "NCBITaxon_9606"), ncbiTaxon);
    }

    @Test
    public void shouldResolveADatedReleaseByItsShortName() throws Exception {
        // Matching a full public URL would miss this, and every local working copy too.
        OWLOntology release = ontology(OBO + "go/releases/2026-01-01/go.owl");

        assertOwner(release, clazz(OBO + "GO_0006915"), release);
    }

    @Test
    public void shouldGiveNoShortNameToAnIriEndingInASlash() throws Exception {
        OWLOntology trailing = ontology(OBO + "go/");

        assertNoOwner(trailing, clazz(OBO + "GO_0006915"));
    }

    @Test
    public void shouldNeverInventAnOntologyFromAnUnknownIdSpace() throws Exception {
        OWLOntology mine = ontology(OBO + "mine.owl");

        assertNoOwner(mine, clazz(OBO + "ZZZZ_0000001"));
    }

    @Test
    public void shouldResolveNothingWhenTwoOntologiesShareAShortName() throws Exception {
        OWLOntology go = ontology(OBO + "go.owl");
        OWLOntology alsoGo = ontology(OBO + "go/components/go.owl");

        assertNoOwnerIn(clazz(OBO + "GO_0006915"), go, alsoGo);
    }

    @Test
    public void shouldNeverMakeAnAnonymousOntologyAnOwner() throws Exception {
        OWLOntology anonymous = manager.createOntology();

        assertNoOwner(anonymous, clazz(OBO + "GO_0006915"));
    }

    @Test
    public void shouldResolveNothingForAnEntityWithNoOboShape() throws Exception {
        OWLOntology go = ontology(OBO + "go.owl");

        assertNoOwner(go, clazz("http://example.org/base#Term"));
    }

    private void assertOwner(OWLOntology closure, OWLClass entity, OWLOntology expected) {
        Optional<OWLOntologyID> owner = rule.compile(TestClosureView.over(closure)).resolve(entity);
        assertEquals(Optional.of(expected.getOntologyID()), owner);
    }

    private void assertNoOwnerIn(OWLClass entity, OWLOntology... closure) {
        assertFalse(rule.compile(TestClosureView.over(closure)).resolve(entity).isPresent());
    }

    private void assertNoOwner(OWLOntology closure, OWLClass entity) {
        assertNoOwnerIn(entity, closure);
    }

    private OWLOntology ontology(String iri) throws Exception {
        return manager.createOntology(IRI.create(iri));
    }

    private OWLClass clazz(String iri) {
        return dataFactory.getOWLClass(IRI.create(iri));
    }
}
