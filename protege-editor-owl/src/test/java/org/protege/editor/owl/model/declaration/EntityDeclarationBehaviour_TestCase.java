package org.protege.editor.owl.model.declaration;

import org.junit.Before;
import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.FunctionalSyntaxDocumentFormat;
import org.semanticweb.owlapi.formats.OWLXMLDocumentFormat;
import org.semanticweb.owlapi.formats.RDFXMLDocumentFormat;
import org.semanticweb.owlapi.formats.TurtleDocumentFormat;
import org.semanticweb.owlapi.io.StringDocumentTarget;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLDocumentFormat;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.util.SimpleIRIMapper;

import java.io.File;
import java.util.SortedSet;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Shows which declarations are written when automatic declarations are included or left out.
 *
 * <p>The ontology under test imports a second ontology. The tests verify that declarations in the
 * ontology being saved are always kept, an entity declared only by the import is not copied, and an
 * entity declared by neither ontology is added only when automatic declarations are included. The
 * same rules are checked in RDF/XML, Turtle, OWL/XML, and Functional Syntax.
 */
public class EntityDeclarationBehaviour_TestCase {

    private static final String BASE_NS = "http://example.invalid/declarations/base#";

    private static final String MODULE_NS = "http://example.invalid/declarations/module#";

    private static final String LOCAL_TERM = "Class " + MODULE_NS + "LocalTerm";

    private static final String SHARED_TERM = "Class " + BASE_NS + "SharedTerm";

    private static final String MISPLACED_TERM = "Class " + BASE_NS + "MisplacedTerm";

    /** An entity referenced by the module but not declared by either ontology. */
    private static final String REFERENCED_ONLY = "Class " + BASE_NS + "Borrowed";

    private static final String BASE_ONLY = "Class " + BASE_NS + "BaseOnly";

    private static final File FIXTURES = new File("src/test/resources/declarations/fixtures");

    private OWLOntology module;

    @Before
    public void setUp() throws Exception {
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        // Load the imported ontology from the local test file instead of the network.
        manager.getIRIMappers().add(new SimpleIRIMapper(
                IRI.create("http://example.invalid/declarations/base"),
                IRI.create(new File(FIXTURES, "base.ttl"))));
        module = manager.loadOntologyFromOntologyDocument(new File(FIXTURES, "module.ttl"));
    }

    @Test
    public void shouldLoadTheModuleWithItsImportResolved() {
        assertFalse("the import did not resolve", module.getImports().isEmpty());
        assertTrue("the base ontology should declare SharedTerm",
                module.getImports().iterator().next().getAxiomCount() > 0);
        assertThat(module.getImportsClosure().size(), is(2));
    }

    /** Leaves out the undeclared entity while keeping declarations already in the ontology. */
    @Test
    public void shouldWriteOnlyTheStatedDeclarationsWhenSuppressing() throws Exception {
        for (OWLDocumentFormat format : formats()) {
            SortedSet<String> declarations = declarationsWhenSaved(format, true);
            String where = format.getClass().getSimpleName();

            assertThat(where + ": an automatic declaration was written",
                    declarations, not(hasItem(REFERENCED_ONLY)));
            assertThat(where + ": a declaration from the module is missing",
                    declarations, hasItem(LOCAL_TERM));
            assertThat(where + ": a declaration shared with the import is missing",
                    declarations, hasItem(SHARED_TERM));
            assertThat(where + ": a declaration that uses the import's namespace is missing",
                    declarations, hasItem(MISPLACED_TERM));
        }
    }

    /** The default behavior adds a declaration for an entity that neither ontology declares. */
    @Test
    public void shouldAddTheMissingDeclarationByDefault() throws Exception {
        for (OWLDocumentFormat format : formats()) {
            SortedSet<String> declarations = declarationsWhenSaved(format, false);
            String where = format.getClass().getSimpleName();

            assertThat(where, declarations, hasItem(REFERENCED_ONLY));
            assertThat(where, declarations, hasItem(LOCAL_TERM));
            assertThat(where, declarations, hasItem(SHARED_TERM));
            assertThat(where, declarations, hasItem(MISPLACED_TERM));
        }
    }

    /** A declaration found only in the import is not copied into the saved ontology. */
    @Test
    public void shouldNeverAddADeclarationTheImportAlreadyCarries() throws Exception {
        for (OWLDocumentFormat format : formats()) {
            assertThat(format.getClass().getSimpleName(),
                    declarationsWhenSaved(format, false), not(hasItem(BASE_ONLY)));
            assertThat(format.getClass().getSimpleName(),
                    declarationsWhenSaved(format, true), not(hasItem(BASE_ONLY)));
        }
    }

    private SortedSet<String> declarationsWhenSaved(OWLDocumentFormat format, boolean suppressing)
            throws Exception {
        SaveFormatResolver resolver = new SaveFormatResolver(() -> suppressing);
        StringDocumentTarget target = new StringDocumentTarget();
        module.saveOntology(resolver.getSaveFormat(format), target);
        return DeclarationBaseline.declarationsOf(target.toString());
    }

    /** Returns the four formats covered by the automatic declaration setting. */
    private static OWLDocumentFormat[] formats() {
        return new OWLDocumentFormat[]{
                new RDFXMLDocumentFormat(),
                new TurtleDocumentFormat(),
                new OWLXMLDocumentFormat(),
                new FunctionalSyntaxDocumentFormat()
        };
    }
}
