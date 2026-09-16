package org.protege.editor.owl.model.declaration;

import com.google.common.collect.ImmutableSet;
import org.junit.Before;
import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests how an entity ID is matched to its owning ontology.
 */
public class OwnerIndex_TestCase {

    private static final String OBO = "http://purl.obolibrary.org/obo/";

    private static final Set<OwnershipRule> BOTH_RULES =
            ImmutableSet.of(OwnershipRule.OBO_IDENTIFIER, OwnershipRule.NAMESPACE);

    private OWLOntologyManager manager;

    private OWLDataFactory dataFactory;

    @Before
    public void setUp() {
        manager = OWLManager.createOWLOntologyManager();
        dataFactory = manager.getOWLDataFactory();
    }

    @Test
    public void shouldResolveAHashIriToTheOntologyNamingIt() throws Exception {
        OWLOntology base = ontology("http://example.org/base");
        OwnerIndex index = indexOver(base);

        assertOwner(index, clazz("http://example.org/base#Term"), base, OwnershipRule.NAMESPACE);
    }

    @Test
    public void shouldResolveASlashIriToTheOntologyNamingIt() throws Exception {
        OWLOntology base = ontology("http://example.org/base");
        OwnerIndex index = indexOver(base);

        assertOwner(index, clazz("http://example.org/base/Term"), base, OwnershipRule.NAMESPACE);
    }

    @Test
    public void shouldReadTheOntologyIriRatherThanTheVersionIri() throws Exception {
        OWLOntology versioned = manager.createOntology(new OWLOntologyID(
                com.google.common.base.Optional.of(IRI.create("http://example.org/versioned")),
                com.google.common.base.Optional.of(IRI.create("http://example.org/versioned/2026-09-09"))));
        OwnerIndex index = indexOver(versioned);

        assertOwner(index, clazz("http://example.org/versioned#Term"), versioned, OwnershipRule.NAMESPACE);
    }

    @Test
    public void shouldNeverMakeAnAnonymousOntologyAnOwner() throws Exception {
        OWLOntology anonymous = manager.createOntology();
        OwnerIndex index = indexOver(anonymous);

        assertFalse(index.resolve(clazz("http://example.org/base#Term")).isPresent());
    }

    @Test
    public void shouldResolveNothingForANamespaceNoOntologyOwns() throws Exception {
        OWLOntology mine = ontology("http://example.org/mine");
        OwnerIndex index = indexOver(mine);

        // Declaring a foreign term locally is normal practice, so an absent owner is not a finding.
        assertFalse(index.resolve(clazz("http://xmlns.com/foaf/0.1/Person")).isPresent());
    }

    @Test
    public void shouldResolveAnOboIdSpaceToItsOntology() throws Exception {
        OWLOntology go = ontology(OBO + "go.owl");
        OwnerIndex index = indexOver(go);

        assertOwner(index, clazz(OBO + "GO_0006915"), go, OwnershipRule.OBO_IDENTIFIER);
    }

    @Test
    public void shouldResolveAnIdSpaceContainingAnUnderscore() throws Exception {
        OWLOntology apolloSv = ontology(OBO + "apollo_sv.owl");
        OwnerIndex index = indexOver(apolloSv);

        assertOwner(index, clazz(OBO + "APOLLO_SV_0000001"), apolloSv, OwnershipRule.OBO_IDENTIFIER);
    }

    @Test
    public void shouldResolveAMixedCaseIdSpace() throws Exception {
        OWLOntology ncbiTaxon = ontology(OBO + "ncbitaxon.owl");
        OwnerIndex index = indexOver(ncbiTaxon);

        assertOwner(index, clazz(OBO + "NCBITaxon_9606"), ncbiTaxon, OwnershipRule.OBO_IDENTIFIER);
    }

    @Test
    public void shouldResolveADatedReleaseByItsShortName() throws Exception {
        // Matching a full public URL would miss this, and every local working copy too.
        OWLOntology release = ontology(OBO + "go/releases/2026-01-01/go.owl");
        OwnerIndex index = indexOver(release);

        assertOwner(index, clazz(OBO + "GO_0006915"), release, OwnershipRule.OBO_IDENTIFIER);
    }

    @Test
    public void shouldGiveNoShortNameToAnIriEndingInASlash() throws Exception {
        OWLOntology trailing = ontology(OBO + "go/");
        OwnerIndex index = indexOver(trailing);

        assertFalse(index.resolve(clazz(OBO + "GO_0006915")).isPresent());
    }

    @Test
    public void shouldNeverInventAnOntologyFromAnUnknownIdSpace() throws Exception {
        OWLOntology mine = ontology(OBO + "mine.owl");
        OwnerIndex index = indexOver(mine);

        assertFalse(index.resolve(clazz(OBO + "ZZZZ_0000001")).isPresent());
    }

    @Test
    public void shouldResolveNothingWhenTwoOntologiesShareAShortName() throws Exception {
        OWLOntology go = ontology(OBO + "go.owl");
        OWLOntology alsoGo = ontology(OBO + "go/components/go.owl");
        OwnerIndex index = indexOver(go, alsoGo);

        // Choosing one of them would attribute terms on a coin toss, so neither is chosen.
        assertFalse(index.resolve(clazz(OBO + "GO_0006915")).isPresent());
    }

    @Test
    public void shouldLetTheOboRuleWinWhenBothRulesCouldAnswer() throws Exception {
        OWLOntology namespaceOwner = ontology("http://example.org/ns");
        OWLOntology idSpaceOwner = ontology("http://example.org/go.owl");
        OwnerIndex index = indexOver(namespaceOwner, idSpaceOwner);

        assertOwner(index, clazz("http://example.org/ns/GO_0006915"),
                idSpaceOwner, OwnershipRule.OBO_IDENTIFIER);
    }

    @Test
    public void shouldFallBackToTheNamespaceRuleWhenTheOboRuleIsOff() throws Exception {
        OWLOntology namespaceOwner = ontology("http://example.org/ns");
        OWLOntology idSpaceOwner = ontology("http://example.org/go.owl");
        OwnerIndex index = OwnerIndex.over(idsOf(namespaceOwner, idSpaceOwner),
                ImmutableSet.of(OwnershipRule.NAMESPACE));

        assertOwner(index, clazz("http://example.org/ns/GO_0006915"),
                namespaceOwner, OwnershipRule.NAMESPACE);
    }

    @Test
    public void shouldResolveNothingWhenTheNamespaceRuleIsOff() throws Exception {
        OWLOntology base = ontology("http://example.org/base");
        OwnerIndex index = OwnerIndex.over(idsOf(base),
                ImmutableSet.of(OwnershipRule.OBO_IDENTIFIER));

        assertFalse(index.resolve(clazz("http://example.org/base#Term")).isPresent());
    }

    @Test
    public void shouldResolveNothingWhenBothRulesAreOff() throws Exception {
        OWLOntology go = ontology(OBO + "go.owl");
        OwnerIndex index = OwnerIndex.over(idsOf(go), ImmutableSet.of());

        assertFalse(index.resolve(clazz(OBO + "GO_0006915")).isPresent());
    }

    private static void assertOwner(OwnerIndex index, OWLEntity entity,
                                    OWLOntology expectedOntology, OwnershipRule expectedRule) {
        Optional<DeclarationOwner> owner = index.resolve(entity);
        assertTrue("no owner resolved for " + entity, owner.isPresent());
        assertEquals(expectedOntology.getOntologyID(), owner.get().getOntology());
        assertEquals(expectedRule, owner.get().getRule());
    }

    private OwnerIndex indexOver(OWLOntology... ontologies) {
        return OwnerIndex.over(idsOf(ontologies), BOTH_RULES);
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
