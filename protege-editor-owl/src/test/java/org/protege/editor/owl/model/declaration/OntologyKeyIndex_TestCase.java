package org.protege.editor.owl.model.declaration;

import org.junit.Before;
import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyID;
import org.semanticweb.owlapi.model.OWLOntologyManager;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests the index every ownership rule matches an entity against.
 */
public class OntologyKeyIndex_TestCase {

    private OWLOntologyManager manager;

    @Before
    public void setUp() {
        manager = OWLManager.createOWLOntologyManager();
    }

    @Test
    public void shouldResolveAKeyClaimedByOneOntology() throws Exception {
        OWLOntology base = ontology("http://example.org/base");
        OntologyKeyIndex index = OntologyKeyIndex.over(idsOf(base), OntologyIris::ontologyIriOf);

        assertEquals(Optional.of(base.getOntologyID()), index.lookup("http://example.org/base"));
    }

    @Test
    public void shouldResolveNothingForAKeyClaimedByTwoOntologies() throws Exception {
        OWLOntology one = ontology("http://example.org/one");
        OWLOntology other = ontology("http://example.org/other");
        // Choosing one of them would attribute terms on a coin toss, so neither is chosen.
        OntologyKeyIndex index = OntologyKeyIndex.over(idsOf(one, other), ontology -> Optional.of("shared"));

        assertFalse(index.lookup("shared").isPresent());
    }

    @Test
    public void shouldResolveNothingForAKeyNoOntologyClaims() throws Exception {
        OWLOntology base = ontology("http://example.org/base");
        OntologyKeyIndex index = OntologyKeyIndex.over(idsOf(base), OntologyIris::ontologyIriOf);

        assertFalse(index.lookup("http://example.org/other").isPresent());
    }

    @Test
    public void shouldLeaveOutAnOntologyWithNoKey() throws Exception {
        OWLOntology anonymous = manager.createOntology();
        OntologyKeyIndex index = OntologyKeyIndex.over(idsOf(anonymous), OntologyIris::ontologyIriOf);

        assertFalse(index.lookup("").isPresent());
    }

    @Test
    public void shouldLeaveOutAnEmptyKey() throws Exception {
        OWLOntology base = ontology("http://example.org/base");
        OntologyKeyIndex index = OntologyKeyIndex.over(idsOf(base), ontology -> Optional.of(""));

        assertFalse(index.lookup("").isPresent());
    }

    @Test
    public void shouldNotTreatOneOntologyListedTwiceAsAClash() throws Exception {
        OWLOntology base = ontology("http://example.org/base");
        OntologyKeyIndex index = OntologyKeyIndex.over(idsOf(base, base), OntologyIris::ontologyIriOf);

        assertTrue(index.lookup("http://example.org/base").isPresent());
    }

    private static List<OWLOntologyID> idsOf(OWLOntology... ontologies) {
        return Arrays.stream(ontologies)
                .map(OWLOntology::getOntologyID)
                .collect(Collectors.toList());
    }

    private OWLOntology ontology(String iri) throws Exception {
        return manager.createOntology(IRI.create(iri));
    }
}
