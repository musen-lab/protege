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
 * The acceptance table, one test per row, in every format that adds missing entity declarations.
 *
 * <p>The fixture is a module that imports a base ontology, so the awkward cases are real: a
 * declaration the import also carries, and a declaration whose namespace the import owns.
 */
public class EntityDeclarationBehaviour_TestCase {

    private static final String BASE_NS = "http://example.invalid/declarations/base#";

    private static final String MODULE_NS = "http://example.invalid/declarations/module#";

    private static final String LOCAL_TERM = "Class " + MODULE_NS + "LocalTerm";

    private static final String SHARED_TERM = "Class " + BASE_NS + "SharedTerm";

    private static final String MISPLACED_TERM = "Class " + BASE_NS + "MisplacedTerm";

    /** Referenced by the module, declared by neither ontology: the one Protege declares for you. */
    private static final String REFERENCED_ONLY = "Class " + BASE_NS + "Borrowed";

    private static final String BASE_ONLY = "Class " + BASE_NS + "BaseOnly";

    private static final File FIXTURES = new File("src/test/resources/declarations/fixtures");

    private OWLOntology module;

    @Before
    public void setUp() throws Exception {
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        // Resolve the import locally: the test must not reach the network.
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

    /**
     * Scenarios "An automatic declaration is gone", "An explicit declaration survives",
     * "A duplicated declaration is still written" and "A declaration in a namespace an import owns
     * is still written".
     */
    @Test
    public void shouldWriteOnlyTheStatedDeclarationsWhenSuppressing() throws Exception {
        for (OWLDocumentFormat format : formats()) {
            SortedSet<String> declarations = declarationsWhenSaved(format, true);
            String where = format.getClass().getSimpleName();

            assertThat(where + ": the automatic declaration should be gone",
                    declarations, not(hasItem(REFERENCED_ONLY)));
            assertThat(where + ": an explicit declaration must survive",
                    declarations, hasItem(LOCAL_TERM));
            assertThat(where + ": a declaration the import also carries must survive",
                    declarations, hasItem(SHARED_TERM));
            assertThat(where + ": a declaration in the import's namespace must survive",
                    declarations, hasItem(MISPLACED_TERM));
        }
    }

    /**
     * Scenario "An undeclared entity keeps the type Protege adds for it".
     */
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

    /**
     * An entity the import declares is never declared here, whatever the preference says.
     */
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

    /**
     * Every format whose renderer adds missing declarations, and so every format the preference
     * reaches.
     */
    private static OWLDocumentFormat[] formats() {
        return new OWLDocumentFormat[]{
                new RDFXMLDocumentFormat(),
                new TurtleDocumentFormat(),
                new OWLXMLDocumentFormat(),
                new FunctionalSyntaxDocumentFormat()
        };
    }
}
