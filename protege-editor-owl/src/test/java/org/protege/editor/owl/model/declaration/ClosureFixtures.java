package org.protege.editor.owl.model.declaration;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;

/**
 * Provides the in-memory import closures shared by the declaration tests.
 *
 * <p>Every ontology in a fixture belongs to the same manager, allowing
 * {@link OWLOntology#getImportsClosure()} to resolve all import declarations without file or
 * network access.
 */
final class ClosureFixtures {

    static final String BASE = "http://example.org/base";
    static final String MID  = "http://example.org/mid";
    static final String LEAF = "http://example.org/leaf";
    static final String OBO  = "http://purl.obolibrary.org/obo/";

    private ClosureFixtures() {}

    static IRI iri(String s) { return IRI.create(s); }

    /**
     * Creates a three-level import closure: {@code leaf -> mid -> base}.
     *
     * <ul>
     *   <li>{@code base} declares class {@code A} and object property {@code p};</li>
     *   <li>{@code mid} declares {@code B}, makes it a subclass of {@code A}, and references the
     *       undeclared class {@code Ghost};</li>
     *   <li>{@code leaf} declares {@code C} and {@code base#Misplaced}, placing the latter outside
     *       the ontology identified by its namespace.</li>
     * </ul>
     *
     * @return the leaf ontology, whose manager also contains the middle and base ontologies
     * @throws OWLOntologyCreationException if an ontology in the fixture cannot be created
     */
    static OWLOntology threeLevelClosure() throws OWLOntologyCreationException {
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = m.getOWLDataFactory();

        OWLOntology base = m.createOntology(iri(BASE));
        OWLClass a = df.getOWLClass(iri(BASE + "#A"));
        OWLObjectProperty p = df.getOWLObjectProperty(iri(BASE + "#p"));
        m.addAxiom(base, df.getOWLDeclarationAxiom(a));
        m.addAxiom(base, df.getOWLDeclarationAxiom(p));

        OWLOntology mid = m.createOntology(iri(MID));
        m.applyChange(new AddImport(mid, df.getOWLImportsDeclaration(iri(BASE))));
        OWLClass b = df.getOWLClass(iri(MID + "#B"));
        OWLClass ghost = df.getOWLClass(iri(MID + "#Ghost"));
        m.addAxiom(mid, df.getOWLDeclarationAxiom(b));
        m.addAxiom(mid, df.getOWLSubClassOfAxiom(b, a));
        // Ghost is referenced by an axiom but never declared anywhere.
        m.addAxiom(mid, df.getOWLSubClassOfAxiom(b, ghost));

        OWLOntology leaf = m.createOntology(iri(LEAF));
        m.applyChange(new AddImport(leaf, df.getOWLImportsDeclaration(iri(MID))));
        OWLClass c = df.getOWLClass(iri(LEAF + "#C"));
        OWLClass misplaced = df.getOWLClass(iri(BASE + "#Misplaced"));
        m.addAxiom(leaf, df.getOWLDeclarationAxiom(c));
        // Declaration sits in leaf; the entity's namespace belongs to base.
        m.addAxiom(leaf, df.getOWLDeclarationAxiom(misplaced));
        m.addAxiom(leaf, df.getOWLSubClassOfAxiom(c, misplaced));

        return leaf;
    }

    /**
     * Creates a UBERON ontology that imports GO and declares a GO term not declared by GO.
     *
     * @return the UBERON ontology at the root of the two-ontology closure
     * @throws OWLOntologyCreationException if an ontology in the fixture cannot be created
     */
    static OWLOntology oboClosure() throws OWLOntologyCreationException {
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = m.getOWLDataFactory();

        String obo = "http://purl.obolibrary.org/obo/";
        OWLOntology go = m.createOntology(iri(obo + "go.owl"));
        OWLClass goTerm = df.getOWLClass(iri(obo + "GO_0008150"));
        m.addAxiom(go, df.getOWLDeclarationAxiom(goTerm));

        OWLOntology uberon = m.createOntology(iri(obo + "uberon.owl"));
        m.applyChange(new AddImport(uberon, df.getOWLImportsDeclaration(iri(obo + "go.owl"))));
        OWLClass uberonTerm = df.getOWLClass(iri(obo + "UBERON_0000955"));
        m.addAxiom(uberon, df.getOWLDeclarationAxiom(uberonTerm));
        // uberon declares a GO term it does not own: misplaced under OBO convention.
        OWLClass strayGoTerm = df.getOWLClass(iri(obo + "GO_0006915"));
        m.addAxiom(uberon, df.getOWLDeclarationAxiom(strayGoTerm));

        return uberon;
    }

    /**
     * Creates a generated import module whose owning ontology is not loaded.
     *
     * @return the CL ontology at the root of the closure
     * @throws OWLOntologyCreationException if an ontology in the fixture cannot be created
     */
    static OWLOntology absentOwnerClosure() throws OWLOntologyCreationException {
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = m.getOWLDataFactory();

        IRI moduleIri = iri(OBO + "cl/imports/merged_import.owl");
        OWLOntology module = m.createOntology(moduleIri);
        m.addAxiom(module, df.getOWLDeclarationAxiom(df.getOWLClass(iri(OBO + "GO_0008150"))));

        OWLOntology cl = m.createOntology(iri(OBO + "cl.owl"));
        m.applyChange(new AddImport(cl, df.getOWLImportsDeclaration(moduleIri)));
        return cl;
    }

    /**
     * Creates a GO ontology that repeats a term declared by its CHEBI import.
     *
     * @return the GO ontology at the root of the closure
     * @throws OWLOntologyCreationException if an ontology in the fixture cannot be created
     */
    static OWLOntology declaringOwnerClosure() throws OWLOntologyCreationException {
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = m.getOWLDataFactory();

        OWLClass chebiTerm = df.getOWLClass(iri(OBO + "CHEBI_10022"));
        IRI chebiIri = iri(OBO + "chebi.owl");
        OWLOntology chebi = m.createOntology(chebiIri);
        m.addAxiom(chebi, df.getOWLDeclarationAxiom(chebiTerm));

        OWLOntology go = m.createOntology(iri(OBO + "go.owl"));
        m.applyChange(new AddImport(go, df.getOWLImportsDeclaration(chebiIri)));
        m.addAxiom(go, df.getOWLDeclarationAxiom(chebiTerm));
        return go;
    }

    /**
     * Creates a GO ontology whose terms are declared in an imported component.
     *
     * @return the editing ontology at the root of the closure
     * @throws OWLOntologyCreationException if an ontology in the fixture cannot be created
     */
    static OWLOntology componentLayoutClosure() throws OWLOntologyCreationException {
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = m.getOWLDataFactory();

        IRI componentIri = iri(OBO + "go/components/terms.owl");
        OWLOntology component = m.createOntology(componentIri);
        m.addAxiom(component, df.getOWLDeclarationAxiom(df.getOWLClass(iri(OBO + "GO_0008150"))));

        OWLOntology edit = m.createOntology(iri(OBO + "go.owl"));
        m.applyChange(new AddImport(edit, df.getOWLImportsDeclaration(componentIri)));
        return edit;
    }

    /**
     * Creates a GOA ontology that imports GO and declares an undeclared GO term.
     *
     * @return the GOA ontology at the root of the closure
     * @throws OWLOntologyCreationException if an ontology in the fixture cannot be created
     */
    static OWLOntology siblingProjectClosure() throws OWLOntologyCreationException {
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = m.getOWLDataFactory();

        IRI goIri = iri(OBO + "go.owl");
        OWLOntology go = m.createOntology(goIri);
        m.addAxiom(go, df.getOWLDeclarationAxiom(df.getOWLClass(iri(OBO + "GO_0008150"))));

        OWLOntology goa = m.createOntology(iri(OBO + "goa.owl"));
        m.applyChange(new AddImport(goa, df.getOWLImportsDeclaration(goIri)));
        m.addAxiom(goa, df.getOWLDeclarationAxiom(df.getOWLClass(iri(OBO + "GO_0006915"))));
        return goa;
    }

    /**
     * Creates an ontology with the ID space {@code APOLLO_SV}.
     *
     * @return the ontology at the root of the closure, which declares the stray term
     * @throws OWLOntologyCreationException if an ontology in the fixture cannot be created
     */
    static OWLOntology underscoredPrefixClosure() throws OWLOntologyCreationException {
        return strayTermClosure("apollo_sv.owl", "APOLLO_SV_0000001");
    }

    /**
     * Creates an ontology with the mixed-case ID space {@code NCBITaxon}.
     *
     * @return the ontology at the root of the closure, which declares the stray term
     * @throws OWLOntologyCreationException if an ontology in the fixture cannot be created
     */
    static OWLOntology mixedCasePrefixClosure() throws OWLOntologyCreationException {
        return strayTermClosure("ncbitaxon.owl", "NCBITaxon_9606");
    }

    /**
     * Creates a root ontology that declares a term owned by an import.
     *
     * @param ownerName the file name of the owning ontology
     * @param strayTermId the ID of the term declared by the root
     * @return the root ontology, which imports the owner
     * @throws OWLOntologyCreationException if an ontology in the fixture cannot be created
     */
    private static OWLOntology strayTermClosure(String ownerName, String strayTermId)
            throws OWLOntologyCreationException {
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = m.getOWLDataFactory();

        IRI ownerIri = iri(OBO + ownerName);
        // The owner is loaded and stays silent about the term the root claims.
        m.createOntology(ownerIri);

        OWLOntology root = m.createOntology(iri(OBO + "mine.owl"));
        m.applyChange(new AddImport(root, df.getOWLImportsDeclaration(ownerIri)));
        m.addAxiom(root, df.getOWLDeclarationAxiom(df.getOWLClass(iri(OBO + strayTermId))));
        return root;
    }
}
