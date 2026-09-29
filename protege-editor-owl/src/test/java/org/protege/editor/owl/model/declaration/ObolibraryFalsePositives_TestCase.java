package org.protege.editor.owl.model.declaration;

import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

/**
 * Tests that valid OBO declarations are not reported as misplaced.
 */
public class ObolibraryFalsePositives_TestCase {

    private static final String OBO = "http://purl.obolibrary.org/obo/";

    private static final Pattern OBO_ID_IRI = Pattern.compile("/(([A-Z]|[a-z])+(_([A-Z]|[a-z])+)?)_(\\d+)$");

    private static Optional<String> idPrefix(IRI iri) {
        Matcher m = OBO_ID_IRI.matcher(iri.toString());
        return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    }

    /**
     * Finds an entity's owning ontology from its OBO ID space.
     *
     * @param e the entity to find
     * @param root the ontology to search, including its imports
     * @return the owning ontology, or an empty value if it is not loaded
     */
    private static Optional<OWLOntology> owner(OWLEntity e, OWLOntology root) {
        Optional<String> prefix = idPrefix(e.getIRI());
        if (!prefix.isPresent()) return Optional.empty();
        String wanted = OBO + prefix.get().toLowerCase() + ".owl";
        return root.getImportsClosure().stream()
                .filter(o -> !o.getOntologyID().isAnonymous())
                .filter(o -> o.getOntologyID().getOntologyIRI().get().toString().equals(wanted))
                .findFirst();
    }

    private static boolean isMisplaced(OWLEntity e, OWLOntology root) {
        Optional<OWLOntology> owner = owner(e, root);
        if (!owner.isPresent()) return false;                       // precondition
        if (!owner.get().getDeclarationAxioms(e).isEmpty()) return false;  // owner declares it
        return root.getImportsClosure().stream()
                .anyMatch(o -> !o.equals(owner.get()) && !o.getDeclarationAxioms(e).isEmpty());
    }

    @Test
    public void shouldNotFlagAGeneratedImportModuleDeclaringForeignTerms() throws Exception {
        // The CL shape: cl.owl imports cl/imports/merged_import.owl, which declares
        // GO and UBERON terms. Full go.owl is NOT in the closure.
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = m.getOWLDataFactory();
        IRI moduleIri = IRI.create(OBO + "cl/imports/merged_import.owl");
        OWLOntology module = m.createOntology(moduleIri);
        OWLClass goTerm = df.getOWLClass(IRI.create(OBO + "GO_0008150"));
        m.addAxiom(module, df.getOWLDeclarationAxiom(goTerm));

        OWLOntology cl = m.createOntology(IRI.create(OBO + "cl.owl"));
        m.applyChange(new AddImport(cl, df.getOWLImportsDeclaration(moduleIri)));

        assertFalse("go.owl is not in the closure, so there is no owner to compare against",
                owner(goTerm, cl).isPresent());
        assertFalse(isMisplaced(goTerm, cl));
    }

    @Test
    public void shouldNotFlagABaseReleaseCarryingForeignDeclarationsWhenTheOwnerIsLoaded() throws Exception {
        // go-base.owl carries thousands of bare CHEBI declarations. Load chebi.owl
        // alongside and the owner is present - but it declares the term itself.
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = m.getOWLDataFactory();
        OWLClass chebiTerm = df.getOWLClass(IRI.create(OBO + "CHEBI_10022"));

        IRI chebiIri = IRI.create(OBO + "chebi.owl");
        OWLOntology chebi = m.createOntology(chebiIri);
        m.addAxiom(chebi, df.getOWLDeclarationAxiom(chebiTerm));

        OWLOntology goBase = m.createOntology(IRI.create(OBO + "go.owl"));
        m.applyChange(new AddImport(goBase, df.getOWLImportsDeclaration(chebiIri)));
        m.addAxiom(goBase, df.getOWLDeclarationAxiom(chebiTerm));   // the bare foreign declaration

        assertTrue("chebi.owl is in the closure", owner(chebiTerm, goBase).isPresent());
        assertFalse("owner declares it, so nothing is misplaced", isMisplaced(chebiTerm, goBase));
    }

    @Test
    public void shouldFlagATermAttributedToAnOntologyThatDoesNotDefineIt() throws Exception {
        // The real smell: go.owl is loaded, and something claims a GO id that
        // go.owl does not define. A stale import, or an id minted in GO's space.
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = m.getOWLDataFactory();
        IRI goIri = IRI.create(OBO + "go.owl");
        OWLOntology go = m.createOntology(goIri);
        m.addAxiom(go, df.getOWLDeclarationAxiom(df.getOWLClass(IRI.create(OBO + "GO_0008150"))));

        OWLOntology uberon = m.createOntology(IRI.create(OBO + "uberon.owl"));
        m.applyChange(new AddImport(uberon, df.getOWLImportsDeclaration(goIri)));
        OWLClass stray = df.getOWLClass(IRI.create(OBO + "GO_0006915"));
        m.addAxiom(uberon, df.getOWLDeclarationAxiom(stray));

        assertTrue(owner(stray, uberon).isPresent());
        assertTrue("go.owl is loaded and does not define this id", isMisplaced(stray, uberon));
    }

    @Test
    public void shouldNeverGuessAnOntologyFromAnUnknownPrefix() throws Exception {
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = m.getOWLDataFactory();
        OWLOntology o = m.createOntology(IRI.create(OBO + "mine.owl"));
        OWLClass exotic = df.getOWLClass(IRI.create(OBO + "ZZZZ_0000001"));
        m.addAxiom(o, df.getOWLDeclarationAxiom(exotic));

        assertEquals(Optional.of("ZZZZ"), idPrefix(exotic.getIRI()));
        assertFalse("no zzzz.owl in the closure, so no finding", isMisplaced(exotic, o));
    }
}
