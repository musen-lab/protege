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

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * Tests how an entity IRI namespace is matched to the ontology that owns it.
 */
public class NamespaceRule_TestCase {

    private OWLOntologyManager manager;

    private OWLDataFactory dataFactory;

    private NamespaceRule rule;

    @Before
    public void setUp() {
        manager = OWLManager.createOWLOntologyManager();
        dataFactory = manager.getOWLDataFactory();
        rule = new NamespaceRule();
    }

    @Test
    public void shouldKeepTheIdentifierItsSettingIsStoredUnder() {
        assertEquals("misplaced.rule.use.namespace", rule.getId());
    }

    @Test
    public void shouldResolveAHashIriToTheOntologyNamingIt() throws Exception {
        OWLOntology base = ontology("http://example.org/base");

        assertOwner(base, clazz("http://example.org/base#Term"), base);
    }

    @Test
    public void shouldResolveASlashIriToTheOntologyNamingIt() throws Exception {
        OWLOntology base = ontology("http://example.org/base");

        assertOwner(base, clazz("http://example.org/base/Term"), base);
    }

    @Test
    public void shouldReadTheOntologyIriRatherThanTheVersionIri() throws Exception {
        OWLOntology versioned = manager.createOntology(new OWLOntologyID(
                com.google.common.base.Optional.of(IRI.create("http://example.org/versioned")),
                com.google.common.base.Optional.of(IRI.create("http://example.org/versioned/2026-09-09"))));

        assertOwner(versioned, clazz("http://example.org/versioned#Term"), versioned);
    }

    @Test
    public void shouldNeverMakeAnAnonymousOntologyAnOwner() throws Exception {
        OWLOntology anonymous = manager.createOntology();

        assertNoOwner(anonymous, clazz("http://example.org/base#Term"));
    }

    @Test
    public void shouldResolveNothingForANamespaceNoOntologyOwns() throws Exception {
        OWLOntology mine = ontology("http://example.org/mine");

        // Declaring a foreign term locally is normal practice, so an absent owner is not a finding.
        assertNoOwner(mine, clazz("http://xmlns.com/foaf/0.1/Person"));
    }

    private void assertOwner(OWLOntology closure, OWLClass entity, OWLOntology expected) {
        Optional<OWLOntologyID> owner = rule.compile(idsOf(closure)).resolve(entity);
        assertEquals(Optional.of(expected.getOntologyID()), owner);
    }

    private void assertNoOwner(OWLOntology closure, OWLClass entity) {
        assertFalse(rule.compile(idsOf(closure)).resolve(entity).isPresent());
    }

    private static List<OWLOntologyID> idsOf(OWLOntology... ontologies) {
        return Arrays.stream(ontologies)
                .map(OWLOntology::getOntologyID)
                .collect(Collectors.toList());
    }

    private OWLOntology ontology(String iri) throws Exception {
        return manager.createOntology(IRI.create(iri));
    }

    private OWLClass clazz(String iri) {
        return dataFactory.getOWLClass(IRI.create(iri));
    }
}
