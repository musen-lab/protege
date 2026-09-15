package org.protege.editor.owl.model.declaration;

import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.RDFXMLDocumentFormat;
import org.semanticweb.owlapi.formats.TurtleDocumentFormat;
import org.semanticweb.owlapi.io.StringDocumentTarget;
import org.semanticweb.owlapi.model.*;

import static org.junit.Assert.*;

/**
 * Verifies that suppressing generated declarations is distinct from removing duplicates.
 *
 * <p>When {@code addMissingTypes} is enabled, the OWL API serializers add a type statement for an
 * entity that is undeclared throughout the import closure. They do not add one when an imported
 * ontology already declares that entity. Consequently, disabling generated declarations affects
 * genuinely undeclared entities but does not remove redundant declarations from the active
 * ontology.
 */
public class SuppressionIsNotDeduplication_TestCase {

    /**
     * Creates an ontology that references a class declared by an import.
     *
     * @return the middle ontology, which imports the base ontology that declares {@code A}
     * @throws Exception if either ontology cannot be created
     */
    private static OWLOntology midReferencingAnImportedClass() throws Exception {
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = m.getOWLDataFactory();

        OWLOntology base = m.createOntology(IRI.create("http://example.org/dd/base"));
        OWLClass a = df.getOWLClass(IRI.create("http://example.org/dd/base#A"));
        m.addAxiom(base, df.getOWLDeclarationAxiom(a));

        OWLOntology mid = m.createOntology(IRI.create("http://example.org/dd/mid"));
        m.applyChange(new AddImport(mid, df.getOWLImportsDeclaration(base.getOntologyID().getOntologyIRI().get())));
        OWLClass b = df.getOWLClass(IRI.create("http://example.org/dd/mid#B"));
        m.addAxiom(mid, df.getOWLDeclarationAxiom(b));
        m.addAxiom(mid, df.getOWLSubClassOfAxiom(b, a));
        return mid;
    }

    /**
     * Extracts and normalises the {@code rdf:RDF} element for stable document comparison.
     *
     * @param document the rendered RDF/XML document
     * @return the RDF element without the generator comment or insignificant whitespace
     */
    private static String body(String document) {
        int start = document.indexOf("<rdf:RDF");
        int end = document.indexOf("</rdf:RDF>");
        return document.substring(start, end).replaceAll("\\s+", " ").trim();
    }

    private static String render(OWLOntology o, OWLDocumentFormat format) throws Exception {
        StringDocumentTarget target = new StringDocumentTarget();
        o.getOWLOntologyManager().saveOntology(o, format, target);
        return target.toString();
    }

    @Test
    public void shouldNotRedeclareAClassThatAnImportDeclares() throws Exception {
        OWLOntology mid = midReferencingAnImportedClass();
        RDFXMLDocumentFormat format = new RDFXMLDocumentFormat();
        assertTrue("addMissingTypes defaults to on", format.isAddMissingTypes());

        String out = render(mid, format);

        assertTrue("mid declares its own class", out.contains("mid#B"));
        // The imported class is referenced but gets no synthesised type triple,
        // because base already declares it and mid imports base.
        assertFalse("imported class must not be re-typed in mid",
                out.contains("<owl:Class rdf:about=\"http://example.org/dd/base#A\"/>"));
    }

    @Test
    public void shouldTypeAnEntityNothingInTheClosureDeclares() throws Exception {
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = m.getOWLDataFactory();
        OWLOntology o = m.createOntology(IRI.create("http://example.org/dd/lonely"));
        OWLClass b = df.getOWLClass(IRI.create("http://example.org/dd/lonely#B"));
        OWLClass ghost = df.getOWLClass(IRI.create("http://example.org/dd/lonely#Ghost"));
        m.addAxiom(o, df.getOWLDeclarationAxiom(b));
        m.addAxiom(o, df.getOWLSubClassOfAxiom(b, ghost));

        RDFXMLDocumentFormat on = new RDFXMLDocumentFormat();
        String withTypes = render(o, on);

        RDFXMLDocumentFormat off = new RDFXMLDocumentFormat();
        off.setAddMissingTypes(false);
        String withoutTypes = render(o, off);

        // With the switch on, the undeclared entity still gets a type triple, so
        // the document round-trips. With it off, that safety net is gone: this is
        // exactly the gap T4.3 has to detect.
        assertTrue("default output types the undeclared entity",
                withTypes.contains("lonely#Ghost"));
        assertTrue(withTypes.length() > withoutTypes.length());
        System.out.println("[dedup] addMissingTypes on: " + withTypes.length()
                + " chars, off: " + withoutTypes.length() + " chars");
    }

    @Test
    public void shouldNotRedeclareAClassThatAnImportDeclaresInTurtle() throws Exception {
        OWLOntology mid = midReferencingAnImportedClass();
        TurtleDocumentFormat format = new TurtleDocumentFormat();
        assertTrue(format.isAddMissingTypes());

        String out = render(mid, format);

        assertTrue(out.contains("mid#B"));
        assertFalse("imported class must not be re-typed in mid",
                out.contains("base#A> rdf:type owl:Class"));
    }

    @Test
    public void shouldChangeNothingForAnEntityTheImportAlreadyDeclares() throws Exception {
        OWLOntology mid = midReferencingAnImportedClass();

        RDFXMLDocumentFormat on = new RDFXMLDocumentFormat();
        RDFXMLDocumentFormat off = new RDFXMLDocumentFormat();
        off.setAddMissingTypes(false);

        // Identical RDF either way: there was never a redundant declaration to
        // remove. Suppression is not de-duplication. Only the trailing generator
        // comment and whitespace differ, so the comparison is over the graph body.
        assertEquals(body(render(mid, on)), body(render(mid, off)));
    }
}
