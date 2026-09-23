package org.protege.editor.owl.model.declaration;

import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyID;
import org.semanticweb.owlapi.model.OWLOntologyManager;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests how the enabled rules are consulted for one run.
 */
public class OwnershipPolicy_TestCase {

    private static final List<OwnershipRule> BOTH_RULES = OwnershipRules.registered();

    private OWLOntologyManager manager;

    private OWLDataFactory dataFactory;

    @Before
    public void setUp() {
        manager = OWLManager.createOWLOntologyManager();
        dataFactory = manager.getOWLDataFactory();
    }

    @Test
    public void shouldLetTheOboRuleWinWhenBothRulesCouldAnswer() throws Exception {
        OWLOntology namespaceOwner = ontology("http://example.org/ns");
        OWLOntology idSpaceOwner = ontology("http://example.org/go.owl");
        OwnershipPolicy policy = policyOf(BOTH_RULES, namespaceOwner, idSpaceOwner);

        assertOwner(policy, clazz("http://example.org/ns/GO_0006915"),
                idSpaceOwner, OboIdentifierRule.ID);
    }

    @Test
    public void shouldFallBackToTheNamespaceRuleWhenTheOboRuleIsOff() throws Exception {
        OWLOntology namespaceOwner = ontology("http://example.org/ns");
        OWLOntology idSpaceOwner = ontology("http://example.org/go.owl");
        OwnershipPolicy policy = policyOf(ImmutableList.of(new NamespaceRule()),
                namespaceOwner, idSpaceOwner);

        assertOwner(policy, clazz("http://example.org/ns/GO_0006915"),
                namespaceOwner, NamespaceRule.ID);
    }

    @Test
    public void shouldResolveNothingWhenTheNamespaceRuleIsOff() throws Exception {
        OWLOntology base = ontology("http://example.org/base");
        OwnershipPolicy policy = policyOf(ImmutableList.of(new OboIdentifierRule()), base);

        assertFalse(policy.resolve(clazz("http://example.org/base#Term")).isPresent());
    }

    @Test
    public void shouldResolveNothingWhenEveryRuleIsOff() throws Exception {
        OWLOntology go = ontology("http://purl.obolibrary.org/obo/go.owl");
        OwnershipPolicy policy = policyOf(ImmutableList.of(), go);

        assertFalse(policy.resolve(clazz("http://purl.obolibrary.org/obo/GO_0006915")).isPresent());
    }

    @Test
    public void shouldConsultTheRulesInTheOrderTheyAreGiven() throws Exception {
        OWLOntology namespaceOwner = ontology("http://example.org/ns");
        OWLOntology idSpaceOwner = ontology("http://example.org/go.owl");
        // Reversing the order reverses which rule decides, and nothing else.
        OwnershipPolicy policy = policyOf(ImmutableList.of(new NamespaceRule(), new OboIdentifierRule()),
                namespaceOwner, idSpaceOwner);

        assertOwner(policy, clazz("http://example.org/ns/GO_0006915"),
                namespaceOwner, NamespaceRule.ID);
    }

    @Test
    public void shouldRecordTheIdentifierOfTheDecidingRule() throws Exception {
        OWLOntology base = ontology("http://example.org/base");
        OwnershipPolicy policy = policyOf(BOTH_RULES, base);

        Optional<DeclarationOwner> owner = policy.resolve(clazz("http://example.org/base#Term"));
        assertTrue(owner.isPresent());
        assertEquals(NamespaceRule.ID, owner.get().getRuleId());
    }

    private static void assertOwner(OwnershipPolicy policy, OWLClass entity,
                                    OWLOntology expectedOntology, String expectedRuleId) {
        Optional<DeclarationOwner> owner = policy.resolve(entity);
        assertTrue("no owner resolved for " + entity, owner.isPresent());
        assertEquals(expectedOntology.getOntologyID(), owner.get().getOntology());
        assertEquals(expectedRuleId, owner.get().getRuleId());
    }

    private static OwnershipPolicy policyOf(List<OwnershipRule> rules, OWLOntology... ontologies) {
        return OwnershipPolicy.over(rules, TestClosureView.over(ontologies));
    }

    private OWLOntology ontology(String iri) throws Exception {
        return manager.createOntology(IRI.create(iri));
    }

    private OWLClass clazz(String iri) {
        return dataFactory.getOWLClass(IRI.create(iri));
    }
}
