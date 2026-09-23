package org.protege.editor.owl.model.declaration;

import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;

import static org.junit.Assert.*;

/**
 * Tests ways to find the ontology that owns an entity ID.
 */
public class MisplacedHeuristic_TestCase {

    /**
     * Checks whether an entity's namespace matches an ontology IRI.
     *
     * @param e the entity to check
     * @param o the possible owner
     * @return {@code true} if the namespace and ontology IRI match
     */
    private static boolean namespaceMatchesOntology(OWLEntity e, OWLOntology o) {
        if (o.getOntologyID().isAnonymous()) return false;
        String ns = e.getIRI().getNamespace();
        String ont = o.getOntologyID().getOntologyIRI().get().toString();
        return stripSeparator(ns).equals(stripSeparator(ont));
    }

    private static String stripSeparator(String s) {
        return s.endsWith("#") || s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    @Test
    public void shouldGiveTheOntologyIriPlusHashAsTheNamespaceOfAHashIri() throws Exception {
        OWLOntology leaf = ClosureFixtures.threeLevelClosure();
        OWLDataFactory df = leaf.getOWLOntologyManager().getOWLDataFactory();
        OWLClass a = df.getOWLClass(IRI.create(ClosureFixtures.BASE + "#A"));

        assertEquals(ClosureFixtures.BASE + "#", a.getIRI().getNamespace());
        assertEquals("A", a.getIRI().getRemainder().get());
    }

    @Test
    public void shouldStopAtTheLastSlashForTheNamespaceOfASlashIri() throws Exception {
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = m.getOWLDataFactory();
        OWLClass a = df.getOWLClass(IRI.create("http://example.org/slash/A"));

        assertEquals("http://example.org/slash/", a.getIRI().getNamespace());
        assertEquals("A", a.getIRI().getRemainder().get());
    }

    @Test
    public void shouldFlagADeclarationPlacedOutsideItsOwnNamespace() throws Exception {
        OWLOntology leaf = ClosureFixtures.threeLevelClosure();
        OWLDataFactory df = leaf.getOWLOntologyManager().getOWLDataFactory();
        OWLClass misplaced = df.getOWLClass(IRI.create(ClosureFixtures.BASE + "#Misplaced"));

        OWLOntology declarer = null;
        OWLOntology owner = null;
        for (OWLOntology o : leaf.getImportsClosure()) {
            if (!o.getDeclarationAxioms(misplaced).isEmpty()) declarer = o;
            if (namespaceMatchesOntology(misplaced, o)) owner = o;
        }

        assertNotNull(declarer);
        assertNotNull("an ontology matching the namespace exists in the closure", owner);
        assertNotEquals("declared away from the ontology that owns its namespace",
                owner, declarer);
        assertEquals(ClosureFixtures.LEAF, declarer.getOntologyID().getOntologyIRI().get().toString());
        assertEquals(ClosureFixtures.BASE, owner.getOntologyID().getOntologyIRI().get().toString());
    }

    @Test
    public void shouldStayQuietWhenTheDeclarationIsWhereItBelongs() throws Exception {
        OWLOntology leaf = ClosureFixtures.threeLevelClosure();
        OWLDataFactory df = leaf.getOWLOntologyManager().getOWLDataFactory();
        OWLClass a = df.getOWLClass(IRI.create(ClosureFixtures.BASE + "#A"));

        for (OWLOntology o : leaf.getImportsClosure()) {
            if (!o.getDeclarationAxioms(a).isEmpty()) {
                assertTrue(namespaceMatchesOntology(a, o));
            }
        }
    }

    @Test
    public void shouldCollapseOnOboIris() throws Exception {
        OWLOntology uberon = ClosureFixtures.oboClosure();
        OWLDataFactory df = uberon.getOWLOntologyManager().getOWLDataFactory();
        OWLClass strayGoTerm = df.getOWLClass(IRI.create("http://purl.obolibrary.org/obo/GO_0006915"));

        assertEquals("http://purl.obolibrary.org/obo/", strayGoTerm.getIRI().getNamespace());

        // No OBO ontology IRI ever equals the shared obo/ namespace, so the naive
        // rule finds no owner and can never fire. It is unusable for OBO content.
        boolean anyOwner = false;
        for (OWLOntology o : uberon.getImportsClosure()) {
            anyOwner |= namespaceMatchesOntology(strayGoTerm, o);
        }
        assertFalse("the namespace rule cannot identify an owner in OBO", anyOwner);
    }

    @Test
    public void shouldNeedTheIdPrefixNotTheNamespaceForOboIris() throws Exception {
        OWLOntology uberon = ClosureFixtures.oboClosure();
        OWLDataFactory df = uberon.getOWLOntologyManager().getOWLDataFactory();
        OWLClass strayGoTerm = df.getOWLClass(IRI.create("http://purl.obolibrary.org/obo/GO_0006915"));

        // The signal OBO actually carries is the local-name prefix, GO_ -> go.owl.
        String remainder = strayGoTerm.getIRI().getRemainder().get();
        assertEquals("GO_0006915", remainder);
        String idPrefix = remainder.substring(0, remainder.indexOf('_'));
        assertEquals("GO", idPrefix);

        OWLOntology owner = null;
        OWLOntology declarer = null;
        for (OWLOntology o : uberon.getImportsClosure()) {
            String ontIri = o.getOntologyID().getOntologyIRI().get().toString();
            String shortName = ontIri.substring(ontIri.lastIndexOf('/') + 1).replace(".owl", "");
            if (shortName.equalsIgnoreCase(idPrefix)) owner = o;
            if (!o.getDeclarationAxioms(strayGoTerm).isEmpty()) declarer = o;
        }

        assertNotNull("the OBO rule finds go.owl as the owner", owner);
        assertNotNull(declarer);
        assertNotEquals(owner, declarer);
        assertEquals("http://purl.obolibrary.org/obo/go.owl",
                owner.getOntologyID().getOntologyIRI().get().toString());
        assertEquals("http://purl.obolibrary.org/obo/uberon.owl",
                declarer.getOntologyID().getOntologyIRI().get().toString());
    }

    @Test
    public void shouldNotFlagAnEntityWithNoOwnerInTheClosure() throws Exception {
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = m.getOWLDataFactory();
        OWLOntology o = m.createOntology(IRI.create("http://example.org/mine"));
        // A foreign entity: nothing in the closure claims its namespace.
        OWLClass foreign = df.getOWLClass(IRI.create("http://xmlns.com/foaf/0.1/Person"));
        m.addAxiom(o, df.getOWLDeclarationAxiom(foreign));

        boolean anyOwner = false;
        for (OWLOntology each : o.getImportsClosure()) {
            anyOwner |= namespaceMatchesOntology(foreign, each);
        }
        // Declaring a foreign entity locally is normal practice, so the rule must
        // fire only when a candidate owner is actually present.
        assertFalse(anyOwner);
    }

    @Test
    public void shouldMatchTheOwnerDespiteAVersionIri() throws Exception {
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = m.getOWLDataFactory();
        OWLOntology o = m.createOntology(new OWLOntologyID(
                com.google.common.base.Optional.of(IRI.create("http://example.org/versioned")),
                com.google.common.base.Optional.of(IRI.create("http://example.org/versioned/2026-09-09"))));
        OWLClass a = df.getOWLClass(IRI.create("http://example.org/versioned#A"));
        m.addAxiom(o, df.getOWLDeclarationAxiom(a));

        assertTrue("the rule must read the ontology IRI, not the version IRI",
                namespaceMatchesOntology(a, o));
    }
}
