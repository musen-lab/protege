package org.protege.editor.owl.model.declaration;

import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

/**
 * Tests how document families affect misplaced declaration findings.
 */
public class DocumentFamilyRule_TestCase {

    private static final String OBO = "http://purl.obolibrary.org/obo/";

    private static final Pattern OBO_ID_IRI =
            Pattern.compile("/(([A-Z]|[a-z])+(_([A-Z]|[a-z])+)?)_(\\d+)$");

    private static Optional<String> idPrefix(IRI iri) {
        Matcher m = OBO_ID_IRI.matcher(iri.toString());
        return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    }

    private static Optional<OWLOntology> owner(OWLEntity e, OWLOntology root) {
        Optional<String> prefix = idPrefix(e.getIRI());
        if (!prefix.isPresent()) {
            return Optional.empty();
        }
        String wanted = OBO + prefix.get().toLowerCase() + ".owl";
        return root.getImportsClosure().stream()
                .filter(o -> !o.getOntologyID().isAnonymous())
                .filter(o -> o.getOntologyID().getOntologyIRI().get().toString().equals(wanted))
                .findFirst();
    }

    private static boolean sameDocumentFamily(OWLOntology candidate, OWLOntology owner) {
        if (candidate.equals(owner)) {
            return true;
        }
        if (candidate.getOntologyID().isAnonymous() || owner.getOntologyID().isAnonymous()) {
            return false;
        }
        String ownerIri = owner.getOntologyID().getOntologyIRI().get().toString();
        String stem = ownerIri.endsWith(".owl")
                ? ownerIri.substring(0, ownerIri.length() - ".owl".length())
                : ownerIri;
        return candidate.getOntologyID().getOntologyIRI().get().toString().startsWith(stem + "/");
    }

    /**
     * Checks for a misplaced declaration without checking document families.
     *
     * @param e the entity to check
     * @param root the ontology to search, including its imports
     * @return {@code true} if another ontology declares the entity instead of its owner
     */
    private static boolean isMisplacedWithoutFamilyTest(OWLEntity e, OWLOntology root) {
        Optional<OWLOntology> owner = owner(e, root);
        if (!owner.isPresent() || !owner.get().getDeclarationAxioms(e).isEmpty()) {
            return false;
        }
        return root.getImportsClosure().stream()
                .anyMatch(o -> !o.equals(owner.get()) && !o.getDeclarationAxioms(e).isEmpty());
    }

    /**
     * Checks for a misplaced declaration in another document family.
     *
     * @param e the entity to check
     * @param root the ontology to search, including its imports
     * @return {@code true} if another document family declares the entity instead of its owner
     */
    private static boolean isMisplaced(OWLEntity e, OWLOntology root) {
        Optional<OWLOntology> owner = owner(e, root);
        if (!owner.isPresent() || !owner.get().getDeclarationAxioms(e).isEmpty()) {
            return false;
        }
        return root.getImportsClosure().stream()
                .filter(o -> !sameDocumentFamily(o, owner.get()))
                .anyMatch(o -> !o.getDeclarationAxioms(e).isEmpty());
    }

    /**
     * Creates an edit ontology that imports a component containing its terms.
     *
     * @param m the manager for the test ontologies
     * @return the edit ontology
     * @throws Exception if an ontology cannot be created
     */
    private static OWLOntology odkComponentLayout(OWLOntologyManager m) throws Exception {
        OWLDataFactory df = m.getOWLDataFactory();
        IRI componentIri = IRI.create(OBO + "go/components/terms.owl");
        OWLOntology component = m.createOntology(componentIri);
        m.addAxiom(component, df.getOWLDeclarationAxiom(
                df.getOWLClass(IRI.create(OBO + "GO_0008150"))));
        OWLOntology edit = m.createOntology(IRI.create(OBO + "go.owl"));
        m.applyChange(new AddImport(edit, df.getOWLImportsDeclaration(componentIri)));
        return edit;
    }

    @Test
    public void shouldFlagEveryOwnTermOfAComponentLayoutUnderThreeConditionsAlone() throws Exception {
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        OWLOntology edit = odkComponentLayout(m);
        OWLClass ownTerm = m.getOWLDataFactory().getOWLClass(IRI.create(OBO + "GO_0008150"));

        assertTrue("the active ontology is its own namespace owner", owner(ownTerm, edit).isPresent());
        // This is the defect the fourth condition exists to fix. Kept as a test so
        // that removing the condition fails loudly rather than quietly.
        assertTrue(isMisplacedWithoutFamilyTest(ownTerm, edit));
    }

    @Test
    public void shouldLeaveTheComponentLayoutCleanUnderTheSpecifiedRule() throws Exception {
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        OWLOntology edit = odkComponentLayout(m);
        OWLClass ownTerm = m.getOWLDataFactory().getOWLClass(IRI.create(OBO + "GO_0008150"));

        assertTrue("the owner is still resolved", owner(ownTerm, edit).isPresent());
        assertFalse("a component of the owner is not a foreign declarer",
                isMisplaced(ownTerm, edit));
    }

    @Test
    public void shouldStillCatchTheGenuineDefect() throws Exception {
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = m.getOWLDataFactory();
        IRI goIri = IRI.create(OBO + "go.owl");
        OWLOntology go = m.createOntology(goIri);
        m.addAxiom(go, df.getOWLDeclarationAxiom(df.getOWLClass(IRI.create(OBO + "GO_0008150"))));

        OWLOntology uberon = m.createOntology(IRI.create(OBO + "uberon.owl"));
        m.applyChange(new AddImport(uberon, df.getOWLImportsDeclaration(goIri)));
        OWLClass stray = df.getOWLClass(IRI.create(OBO + "GO_0006915"));
        m.addAxiom(uberon, df.getOWLDeclarationAxiom(stray));

        assertTrue("uberon.owl is not part of go's family", isMisplaced(stray, uberon));
    }

    @Test
    public void shouldTreatAnImportModuleUnderAnotherProductAsForeign() throws Exception {
        // cl/imports/go_import.owl belongs to CL's family, not GO's. If go.owl is
        // loaded and does not define the term, that is still a real finding.
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = m.getOWLDataFactory();
        IRI goIri = IRI.create(OBO + "go.owl");
        OWLOntology go = m.createOntology(goIri);
        m.addAxiom(go, df.getOWLDeclarationAxiom(df.getOWLClass(IRI.create(OBO + "GO_0008150"))));

        IRI clImportIri = IRI.create(OBO + "cl/imports/go_import.owl");
        OWLOntology clImport = m.createOntology(clImportIri);
        OWLClass stray = df.getOWLClass(IRI.create(OBO + "GO_0006915"));
        m.addAxiom(clImport, df.getOWLDeclarationAxiom(stray));

        OWLOntology cl = m.createOntology(IRI.create(OBO + "cl.owl"));
        m.applyChange(new AddImport(cl, df.getOWLImportsDeclaration(goIri)));
        m.applyChange(new AddImport(cl, df.getOWLImportsDeclaration(clImportIri)));

        assertTrue(isMisplaced(stray, cl));
    }

    @Test
    public void shouldNotTreatASiblingProductAsFamily() throws Exception {
        // goa.owl must not count as family of go.owl. The stem comparison appends
        // a slash precisely to prevent this.
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        OWLOntology go = m.createOntology(IRI.create(OBO + "go.owl"));
        OWLOntology goa = m.createOntology(IRI.create(OBO + "goa.owl"));
        OWLOntology component = m.createOntology(IRI.create(OBO + "go/components/x.owl"));

        assertFalse("goa.owl is a different product", sameDocumentFamily(goa, go));
        assertTrue(sameDocumentFamily(component, go));
        assertTrue("an ontology is always its own family", sameDocumentFamily(go, go));
    }
}
