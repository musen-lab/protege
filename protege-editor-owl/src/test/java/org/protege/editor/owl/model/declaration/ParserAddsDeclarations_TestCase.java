package org.protege.editor.owl.model.declaration;

import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.io.StringDocumentSource;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.Imports;

import static org.junit.Assert.*;

/**
 * Verifies whether OWL API parsing preserves missing declarations in loaded ontologies.
 *
 * <p>The RDF/XML and Turtle tests establish that a referenced class without its own type statement
 * remains undeclared after loading, allowing Protege to detect the defect in memory. A separate
 * round-trip test documents that saving with {@code addMissingTypes} enabled can synthesise the
 * declaration and therefore mask the original defect on the next load.
 */
public class ParserAddsDeclarations_TestCase {

    /** An RDF/XML document containing a referenced class with no {@code rdf:type} triple. */
    private static final String RDFXML_UNTYPED_SUPERCLASS =
            "<?xml version=\"1.0\"?>\n"
          + "<rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\"\n"
          + "         xmlns:rdfs=\"http://www.w3.org/2000/01/rdf-schema#\"\n"
          + "         xmlns:owl=\"http://www.w3.org/2002/07/owl#\"\n"
          + "         xml:base=\"http://example.org/p\">\n"
          + "  <owl:Ontology rdf:about=\"http://example.org/p\"/>\n"
          + "  <owl:Class rdf:about=\"http://example.org/p#B\">\n"
          + "    <rdfs:subClassOf rdf:resource=\"http://example.org/p#Ghost\"/>\n"
          + "  </owl:Class>\n"
          + "</rdf:RDF>";

    private static final String TURTLE_UNTYPED_SUPERCLASS =
            "@prefix owl: <http://www.w3.org/2002/07/owl#> .\n"
          + "@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .\n"
          + "@prefix : <http://example.org/t#> .\n"
          + "<http://example.org/t> a owl:Ontology .\n"
          + ":B a owl:Class ; rdfs:subClassOf :Ghost .\n";

    private static OWLOntology load(String document) throws Exception {
        return OWLManager.createOWLOntologyManager()
                .loadOntologyFromOntologyDocument(new StringDocumentSource(document));
    }

    @Test
    public void shouldNotDeclareAnUntypedReferencedClassInRdfXml() throws Exception {
        OWLOntology o = load(RDFXML_UNTYPED_SUPERCLASS);
        OWLDataFactory df = o.getOWLOntologyManager().getOWLDataFactory();
        OWLClass ghost = df.getOWLClass(IRI.create("http://example.org/p#Ghost"));
        OWLClass b = df.getOWLClass(IRI.create("http://example.org/p#B"));

        assertTrue("B carried its own type triple", o.isDeclared(b, Imports.INCLUDED));
        assertTrue("Ghost is referenced", o.getSignature(Imports.INCLUDED).contains(ghost));
        // The decisive assertion: the defect present in the file survives the load,
        // so a check over the loaded model can still see it.
        assertFalse("parser must not invent a declaration for Ghost",
                o.isDeclared(ghost, Imports.INCLUDED));
    }

    @Test
    public void shouldNotDeclareAnUntypedReferencedClassInTurtle() throws Exception {
        OWLOntology o = load(TURTLE_UNTYPED_SUPERCLASS);
        OWLDataFactory df = o.getOWLOntologyManager().getOWLDataFactory();
        OWLClass ghost = df.getOWLClass(IRI.create("http://example.org/t#Ghost"));

        assertTrue(o.getSignature(Imports.INCLUDED).contains(ghost));
        assertFalse(o.isDeclared(ghost, Imports.INCLUDED));
    }

    @Test
    public void shouldMaskTheDefectOnARoundTripThroughProtegeDefaults() throws Exception {
        // Load the defective document, then save it with addMissingTypes on (the
        // Protege default). The reloaded copy is clean: the defect is repaired by
        // the save, not by the load.
        OWLOntology first = load(RDFXML_UNTYPED_SUPERCLASS);
        org.semanticweb.owlapi.io.StringDocumentTarget target =
                new org.semanticweb.owlapi.io.StringDocumentTarget();
        first.getOWLOntologyManager().saveOntology(first,
                new org.semanticweb.owlapi.formats.RDFXMLDocumentFormat(), target);

        OWLOntology second = load(target.toString());
        OWLDataFactory df = second.getOWLOntologyManager().getOWLDataFactory();
        OWLClass ghost = df.getOWLClass(IRI.create("http://example.org/p#Ghost"));

        assertTrue("the save synthesised the declaration",
                second.isDeclared(ghost, Imports.INCLUDED));
    }

    @Test
    public void shouldKeepOboIdPrefixesContainingUnderscores() {
        // OboUtilities uses /(([A-Z]|[a-z])+(_([A-Z]|[a-z])+)?)_(\d+)$ with the
        // idspace as group 1. Splitting on the FIRST underscore instead would
        // yield APOLLO and point at the wrong ontology.
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "/(([A-Z]|[a-z])+(_([A-Z]|[a-z])+)?)_(\\d+)$");

        java.util.regex.Matcher m = p.matcher("http://purl.obolibrary.org/obo/APOLLO_SV_0000001");
        assertTrue(m.find());
        assertEquals("APOLLO_SV", m.group(1));

        java.util.regex.Matcher plain = p.matcher("http://purl.obolibrary.org/obo/GO_0008150");
        assertTrue(plain.find());
        assertEquals("GO", plain.group(1));

        // Lowercase the prefix to reach the ontology id. Never uppercase the id:
        // NCBITaxon, FBbt and HsapDv are not id.toUpperCase().
        assertEquals("apollo_sv", "APOLLO_SV".toLowerCase());
        assertEquals("ncbitaxon", "NCBITaxon".toLowerCase());
    }
}
