package org.protege.editor.owl.model.declaration;

import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.Imports;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Compares declaration lookup strategies at the scale of a modular OBO import closure.
 *
 * <p>The per-entity strategy invokes {@code getDeclarationAxioms()} for every entity in every
 * ontology, giving {@code O(entities * ontologies)} lookups. The alternative visits each
 * declaration axiom once and builds an index. The test verifies that both strategies return the
 * same declaring ontologies and records their elapsed times.
 */
public class ClosureWalkScale_TestCase {

    private static final int ONTOLOGIES = 20;
    private static final int ENTITIES_PER_ONTOLOGY = 5000;

    private OWLOntology buildLargeClosure() throws OWLOntologyCreationException {
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = m.getOWLDataFactory();
        OWLOntology root = m.createOntology(IRI.create("http://example.org/scale/root"));
        for (int i = 0; i < ONTOLOGIES; i++) {
            IRI moduleIri = IRI.create("http://example.org/scale/m" + i);
            OWLOntology module = m.createOntology(moduleIri);
            m.applyChange(new AddImport(root, df.getOWLImportsDeclaration(moduleIri)));
            for (int j = 0; j < ENTITIES_PER_ONTOLOGY; j++) {
                OWLClass c = df.getOWLClass(IRI.create(moduleIri + "#C" + j));
                m.addAxiom(module, df.getOWLDeclarationAxiom(c));
                m.addAxiom(module, df.getOWLSubClassOfAxiom(c, df.getOWLThing()));
            }
        }
        return root;
    }

    @Test
    public void shouldBeatPerEntityLookupAndAgreeWithIt() throws Exception {
        OWLOntology root = buildLargeClosure();
        Set<OWLEntity> signature = root.getSignature(Imports.INCLUDED);
        int expected = ONTOLOGIES * ENTITIES_PER_ONTOLOGY;
        assertEquals(expected + 1 /* owl:Thing */, signature.size());

        long t0 = System.nanoTime();
        Map<OWLEntity, Set<OWLOntology>> byLookup = new HashMap<>();
        for (OWLEntity e : signature) {
            if (e.isBuiltIn()) continue;
            Set<OWLOntology> where = new LinkedHashSet<>();
            for (OWLOntology o : root.getImportsClosure()) {
                if (!o.getDeclarationAxioms(e).isEmpty()) where.add(o);
            }
            byLookup.put(e, where);
        }
        long lookupMs = (System.nanoTime() - t0) / 1_000_000;

        long t1 = System.nanoTime();
        Map<OWLEntity, Set<OWLOntology>> byIndex = new HashMap<>();
        for (OWLOntology o : root.getImportsClosure()) {
            for (OWLAxiom ax : o.getAxioms(AxiomType.DECLARATION)) {
                byIndex.computeIfAbsent(((OWLDeclarationAxiom) ax).getEntity(),
                        k -> new LinkedHashSet<>()).add(o);
            }
        }
        long indexMs = (System.nanoTime() - t1) / 1_000_000;

        System.out.println("[scale] " + root.getImportsClosure().size() + " ontologies, "
                + signature.size() + " entities: per-entity lookup " + lookupMs
                + " ms, single-pass index " + indexMs + " ms");

        for (Map.Entry<OWLEntity, Set<OWLOntology>> entry : byLookup.entrySet()) {
            assertEquals("index and lookup must agree for " + entry.getKey(),
                    entry.getValue(),
                    byIndex.getOrDefault(entry.getKey(), java.util.Collections.emptySet()));
        }
        assertTrue("the index pass must not be slower than the per-entity walk",
                indexMs <= lookupMs);
    }
}
