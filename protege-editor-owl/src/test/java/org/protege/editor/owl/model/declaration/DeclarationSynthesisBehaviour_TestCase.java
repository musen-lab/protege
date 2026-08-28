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
 * The acceptance table, one test per row, in both governed formats.
 *
 * <p>The fixture is a module that imports a base ontology, so the awkward cases are real: a
 * declaration the import also carries, and a declaration whose namespace the import owns.
 */
public class DeclarationSynthesisBehaviour_TestCase {

    private static final String BASE_NS = "http://example.invalid/declarations/base#";

    private static final String MODULE_NS = "http://example.invalid/declarations/module#";

    private static final String LOCAL_TERM = "Class " + MODULE_NS + "LocalTerm";

    private static final String SHARED_TERM = "Class " + BASE_NS + "SharedTerm";

    private static final String MISPLACED_TERM = "Class " + BASE_NS + "MisplacedTerm";

    private static final String BORROWED = "Class " + BASE_NS + "Borrowed";

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
     * Scenarios "A borrowed term loses its type triple", "An explicit declaration survives",
     * "A duplicated declaration is still written" and "A declaration in a namespace an import owns
     * is still written".
     */
    @Test
    public void shouldSuppressOnlyTheInventedDeclarationWhenKeepingThemWithTheDefiner() throws Exception {
        for (OWLDocumentFormat format : governedFormats()) {
            SortedSet<String> declarations = declarationsWhenSaved(format, true);
            String where = format.getClass().getSimpleName();

            assertThat(where + ": the invented declaration should be gone",
                    declarations, not(hasItem(BORROWED)));
            assertThat(where + ": an explicit declaration must survive",
                    declarations, hasItem(LOCAL_TERM));
            assertThat(where + ": a declaration the import also carries must survive",
                    declarations, hasItem(SHARED_TERM));
            assertThat(where + ": a declaration in the import's namespace must survive",
                    declarations, hasItem(MISPLACED_TERM));
        }
    }

    /**
     * Scenario "An undeclared entity keeps its synthesized type".
     */
    @Test
    public void shouldDeclareTheBorrowedEntityByDefault() throws Exception {
        for (OWLDocumentFormat format : governedFormats()) {
            SortedSet<String> declarations = declarationsWhenSaved(format, false);
            String where = format.getClass().getSimpleName();

            assertThat(where, declarations, hasItem(BORROWED));
            assertThat(where, declarations, hasItem(LOCAL_TERM));
            assertThat(where, declarations, hasItem(SHARED_TERM));
            assertThat(where, declarations, hasItem(MISPLACED_TERM));
        }
    }

    /**
     * An entity the import declares is never invented here, whatever the setting says.  Recorded so
     * that a future change to the governed set has to notice it.
     */
    @Test
    public void shouldNeverInventADeclarationTheImportAlreadyCarries() throws Exception {
        for (OWLDocumentFormat format : governedFormats()) {
            assertThat(format.getClass().getSimpleName(),
                    declarationsWhenSaved(format, false), not(hasItem(BASE_ONLY)));
            assertThat(format.getClass().getSimpleName(),
                    declarationsWhenSaved(format, true), not(hasItem(BASE_ONLY)));
        }
    }

    /**
     * Scenario "Formats outside the governed set are unaffected".
     */
    @Test
    public void shouldLeaveUngovernedFormatsAlone() throws Exception {
        OWLDocumentFormat[] ungoverned = {
                new OWLXMLDocumentFormat(),
                new FunctionalSyntaxDocumentFormat()
        };
        for (OWLDocumentFormat format : ungoverned) {
            SortedSet<String> settingOff = declarationsWhenSaved(format, false);
            SortedSet<String> settingOn = declarationsWhenSaved(format, true);
            assertThat(format.getClass().getSimpleName() + " should be unaffected by the setting",
                    settingOn, is(settingOff));
        }
    }

    private SortedSet<String> declarationsWhenSaved(OWLDocumentFormat format, boolean keepInDefiningOntology)
            throws Exception {
        DeclarationSynthesisFormatMapper mapper =
                new DeclarationSynthesisFormatMapper(() -> keepInDefiningOntology);
        StringDocumentTarget target = new StringDocumentTarget();
        module.saveOntology(mapper.mapFormat(format), target);
        return DeclarationBaseline.declarationsOf(target.toString());
    }

    private static OWLDocumentFormat[] governedFormats() {
        return new OWLDocumentFormat[]{new RDFXMLDocumentFormat(), new TurtleDocumentFormat()};
    }
}
