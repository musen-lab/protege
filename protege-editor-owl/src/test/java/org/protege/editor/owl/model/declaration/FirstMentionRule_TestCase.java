package org.protege.editor.owl.model.declaration;

import org.junit.Before;
import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.AddImport;
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
 * Tests how {@link FirstMentionRule} selects the unique earliest mention in an import graph.
 *
 * <p>The cases cover a single mentioner, an importer and its import, a longer import chain,
 * sibling imports, and an import cycle. They establish that the imported document wins along a
 * chain, while unrelated siblings and documents in a cycle produce no owner. They also verify that
 * anonymous ontologies can own entities and that this rule accepts no substitute for its selected
 * owner.
 *
 * <p>Each case builds real OWL ontologies and compiles the rule against a {@link DeclarationIndex}.
 * This exercises the production extraction of mentions and import reachability as well as the
 * rule's selection logic.
 */
public class FirstMentionRule_TestCase {

    private static final String VOCAB = "http://example.org/vocab#";

    private OWLOntologyManager manager;

    private OWLDataFactory dataFactory;

    private FirstMentionRule rule;

    @Before
    public void setUp() {
        manager = OWLManager.createOWLOntologyManager();
        dataFactory = manager.getOWLDataFactory();
        rule = new FirstMentionRule();
    }

    @Test
    public void shouldKeepTheIdentifierItsSettingIsStoredUnder() {
        assertEquals("misplaced.rule.use.first.mention", rule.getId());
    }

    @Test
    public void shouldGiveATermUsedInBothToTheImportedDocument() throws Exception {
        // Y imports X; X says B is an A, and Y says C is a B.
        OWLOntology x = ontology("http://example.org/x");
        OWLOntology y = ontology("http://example.org/y");
        importInto(y, x);
        manager.addAxiom(x, dataFactory.getOWLSubClassOfAxiom(clazz("B"), clazz("A")));
        manager.addAxiom(y, dataFactory.getOWLSubClassOfAxiom(clazz("C"), clazz("B")));

        assertOwner(y, clazz("A"), x);
        assertOwner(y, clazz("B"), x);
    }

    @Test
    public void shouldGiveATermOnlyTheImportingDocumentUsesToThatDocument() throws Exception {
        OWLOntology x = ontology("http://example.org/x");
        OWLOntology y = ontology("http://example.org/y");
        importInto(y, x);
        manager.addAxiom(x, dataFactory.getOWLSubClassOfAxiom(clazz("B"), clazz("A")));
        manager.addAxiom(y, dataFactory.getOWLSubClassOfAxiom(clazz("C"), clazz("B")));

        assertOwner(y, clazz("C"), y);
    }

    @Test
    public void shouldResolveNothingForTwoDocumentsSideBySide() throws Exception {
        OWLOntology one = ontology("http://example.org/one");
        OWLOntology other = ontology("http://example.org/other");
        OWLOntology root = ontology("http://example.org/root");
        importInto(root, one);
        importInto(root, other);
        manager.addAxiom(one, dataFactory.getOWLSubClassOfAxiom(clazz("D"), clazz("Top")));
        manager.addAxiom(other, dataFactory.getOWLSubClassOfAxiom(clazz("D"), clazz("Other")));

        // The term is equally at home in both, so choosing one would be a coin toss.
        assertNoOwner(root, clazz("D"));
    }

    @Test
    public void shouldResolveNothingForDocumentsInAnImportCycle() throws Exception {
        OWLOntology first = ontology("http://example.org/first");
        OWLOntology second = ontology("http://example.org/second");
        importInto(first, second);
        importInto(second, first);
        manager.addAxiom(first, dataFactory.getOWLSubClassOfAxiom(clazz("E"), clazz("Top")));
        manager.addAxiom(second, dataFactory.getOWLSubClassOfAxiom(clazz("E"), clazz("Other")));

        assertNoOwner(first, clazz("E"));
    }

    @Test
    public void shouldGiveATermToTheOnlyDocumentUsingIt() throws Exception {
        OWLOntology base = ontology("http://example.org/base");
        OWLOntology leaf = ontology("http://example.org/leaf");
        importInto(leaf, base);
        manager.addAxiom(base, dataFactory.getOWLDeclarationAxiom(clazz("Solo")));

        assertOwner(leaf, clazz("Solo"), base);
    }

    @Test
    public void shouldLetAnAnonymousDocumentOwnWhatOnlyItUses() throws Exception {
        OWLOntology anonymous = manager.createOntology();
        manager.addAxiom(anonymous, dataFactory.getOWLDeclarationAxiom(clazz("Unsaved")));

        assertOwner(anonymous, clazz("Unsaved"), anonymous);
    }

    @Test
    public void shouldReachThroughALongerChainToTheEarliestDocument() throws Exception {
        OWLOntology base = ontology("http://example.org/base");
        OWLOntology mid = ontology("http://example.org/mid");
        OWLOntology leaf = ontology("http://example.org/leaf");
        importInto(mid, base);
        importInto(leaf, mid);
        for (OWLOntology each : new OWLOntology[]{base, mid, leaf}) {
            manager.addAxiom(each, dataFactory.getOWLDeclarationAxiom(clazz("Shared")));
        }

        assertOwner(leaf, clazz("Shared"), base);
    }

    @Test
    public void shouldStandInForNothing() throws Exception {
        OWLOntology project = ontology("http://example.org/proj");
        OWLOntology part = ontology("http://example.org/proj/part");

        assertFalse("a document beneath the owner's path is still a foreign declarer",
                rule.standsInForOwner(project.getOntologyID(), part.getOntologyID()));
        assertFalse(rule.standsInForOwner(project.getOntologyID(), project.getOntologyID()));
    }

    private void assertOwner(OWLOntology root, OWLClass entity, OWLOntology expected) {
        Optional<OWLOntologyID> owner = rule.compile(DeclarationIndex.over(root)).resolve(entity);
        assertEquals(Optional.of(expected.getOntologyID()), owner);
    }

    private void assertNoOwner(OWLOntology root, OWLClass entity) {
        assertFalse(rule.compile(DeclarationIndex.over(root)).resolve(entity).isPresent());
    }

    private void importInto(OWLOntology importer, OWLOntology imported) {
        manager.applyChange(new AddImport(importer,
                dataFactory.getOWLImportsDeclaration(imported.getOntologyID().getOntologyIRI().get())));
    }

    private OWLOntology ontology(String iri) throws Exception {
        return manager.createOntology(IRI.create(iri));
    }

    private OWLClass clazz(String localName) {
        return dataFactory.getOWLClass(IRI.create(VOCAB + localName));
    }
}
