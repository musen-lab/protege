package org.protege.editor.owl.model.declaration;

import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.util.SimpleIRIMapper;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/**
 * Verifies the declaration checker against a shared module-and-base ontology fixture.
 *
 * <p>The module import is mapped to a local Turtle document, so the tests never access the network;
 * the {@code example.invalid} ontology IRIs are non-resolving names reserved for examples. The
 * fixture exercises undeclared classes, individuals, annotation properties, punning, declarations
 * supplied by an import, and severity classification.
 */
public class MissingDeclarationFixture_TestCase {

    private static final String BASE_NS = "http://example.invalid/declarations/base#";

    private static final String MODULE_NS = "http://example.invalid/declarations/module#";

    private static final File FIXTURES = new File("src/test/resources/declarations/fixtures");

    private OWLOntology module;
    private MissingDeclarationChecker checker;

    @Before
    public void setUp() throws Exception {
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        manager.getIRIMappers().add(new SimpleIRIMapper(
                IRI.create("http://example.invalid/declarations/base"),
                IRI.create(new File(FIXTURES, "base.ttl"))));
        module = manager.loadOntologyFromOntologyDocument(new File(FIXTURES, "module.ttl"));
        checker = new MissingDeclarationChecker();
    }

    @Test
    public void shouldResolveTheImportFromDiskWithoutNetworkAccess() {
        assertEquals(2, module.getImportsClosure().size());
        assertTrue("example.invalid cannot resolve, so a network read would have failed",
                module.getImports().iterator().next().getAxiomCount() > 0);
    }

    @Test
    public void shouldFindExactlyFourUndeclaredEntities() {
        assertEquals(ImmutableList.of(
                BASE_NS + "Borrowed",
                MODULE_NS + "Duo",
                MODULE_NS + "anInstance",
                "http://purl.org/dc/terms/title"),
                names(checker.check(module).getFindings()));
    }

    @Test
    public void shouldClassifyTheBorrowedClassAsAnError() {
        assertEquals(ImmutableList.of(BASE_NS + "Borrowed"),
                names(errorsOnly()));
    }

    @Test
    public void shouldClassifyIndividualsAndTheDublinCorePropertyAsWarnings() {
        assertEquals(ImmutableList.of(
                MODULE_NS + "Duo",
                MODULE_NS + "anInstance",
                "http://purl.org/dc/terms/title"),
                names(checker.check(module).getFindings(DeclarationSeverity.WARNING)));
    }

    @Test
    public void shouldReportAPunnedNameOnlyAsTheUndeclaredIndividual() {
        MissingDeclarationFinding duo = checker.check(module).getFindings().stream()
                .filter(f -> f.getEntity().getIRI().toString().equals(MODULE_NS + "Duo"))
                .findFirst()
                .orElseThrow(AssertionError::new);

        assertEquals(EntityType.NAMED_INDIVIDUAL, duo.getEntityType());
        assertEquals(DeclarationSeverity.WARNING, duo.getSeverity());
    }

    @Test
    public void shouldNotReportEntitiesDeclaredByTheFixture() {
        List<String> reported = names(checker.check(module).getFindings());

        assertFalse(reported.contains(MODULE_NS + "LocalTerm"));
        assertFalse(reported.contains(BASE_NS + "SharedTerm"));
        assertFalse(reported.contains(BASE_NS + "MisplacedTerm"));
        assertFalse("declared by the import, which counts",
                reported.contains(BASE_NS + "BaseOnly"));
    }

    @Test
    public void shouldReturnAnEmptyReportForTheBaseOntologyAlone() {
        OWLOntology base = module.getImports().iterator().next();

        assertTrue(checker.check(base).isEmpty());
    }

    private List<MissingDeclarationFinding> errorsOnly() {
        return checker.check(module).getFindings(DeclarationSeverity.ERROR);
    }

    private static List<String> names(List<MissingDeclarationFinding> findings) {
        return findings.stream()
                .map(f -> f.getEntity().getIRI().toString())
                .collect(Collectors.toList());
    }
}
