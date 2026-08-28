package org.protege.editor.owl.model.declaration;

import org.junit.Before;
import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.RDFXMLDocumentFormat;
import org.semanticweb.owlapi.formats.TurtleDocumentFormat;
import org.semanticweb.owlapi.io.StringDocumentTarget;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDocumentFormat;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;

import java.util.Set;
import java.util.SortedSet;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertTrue;

/**
 * Saving must not disturb the ontology or the format the manager holds for it.
 *
 * <p>These tests reproduce the one line of {@code OWLModelManagerImpl.save} that matters here -
 * read the retained format, map it, write with the result - using the same mapper the production
 * field uses.  They cannot call {@code save} itself: it goes through {@code OntologySaver}, which
 * shows a Swing progress dialog.
 */
public class DeclarationSynthesisSave_TestCase {

    private static final String NS = "http://example.invalid/save#";

    private OWLOntologyManager manager;

    private OWLOntology ontology;

    private OWLClass declared;

    private OWLClass borrowed;

    @Before
    public void setUp() throws Exception {
        manager = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = manager.getOWLDataFactory();
        ontology = manager.createOntology(IRI.create("http://example.invalid/save"));
        declared = df.getOWLClass(IRI.create(NS + "Declared"));
        borrowed = df.getOWLClass(IRI.create(NS + "Borrowed"));
        manager.addAxiom(ontology, df.getOWLDeclarationAxiom(declared));
        manager.addAxiom(ontology, df.getOWLSubClassOfAxiom(declared, borrowed));
        manager.setOntologyFormat(ontology, new RDFXMLDocumentFormat());
    }

    /**
     * Criterion 5: save with the setting off, turn it on, save again.  The two outputs differ and the
     * retained format never changes.
     */
    @Test
    public void shouldNotMutateTheRetainedFormatAcrossTwoSaves() throws Exception {
        assertTrue("precondition", retainedFormat().isAddMissingTypes());
        Set<OWLAxiom> axiomsBefore = ontology.getAxioms();

        String declaringEverything = saveAsIfByModelManager(false);
        assertTrue("the retained format changed during the first save",
                retainedFormat().isAddMissingTypes());

        String keepingWithDefiner = saveAsIfByModelManager(true);
        assertTrue("the retained format changed during the second save",
                retainedFormat().isAddMissingTypes());

        SortedSet<String> first = DeclarationBaseline.declarationsOf(declaringEverything);
        SortedSet<String> second = DeclarationBaseline.declarationsOf(keepingWithDefiner);
        assertThat("the default should invent the borrowed declaration",
                first, hasItem("Class " + NS + "Borrowed"));
        assertThat("keeping declarations with the definer should omit it",
                second, not(hasItem("Class " + NS + "Borrowed")));
        assertThat("the explicit declaration must survive either way",
                second, hasItem("Class " + NS + "Declared"));

        assertThat("saving changed the ontology", ontology.getAxioms(), is(axiomsBefore));
    }

    /**
     * The discriminator against flipping the retained format and putting it back: that approach
     * would hand the saver the very instance the manager holds.  A copy never can.
     */
    @Test
    public void shouldWriteWithACopyRatherThanTheRetainedInstance() {
        OWLDocumentFormat retained = retainedFormat();
        OWLDocumentFormat saveFormat = mapper(true).mapFormat(retained);
        assertThat(saveFormat, not(sameInstance(retained)));
        assertTrue(retained.isAddMissingTypes());
    }

    @Test
    public void shouldLeaveTheAxiomSetUntouchedWhenTheSettingChanges() throws Exception {
        Set<OWLAxiom> before = ontology.getAxioms();
        saveAsIfByModelManager(true);
        saveAsIfByModelManager(false);
        assertThat(ontology.getAxioms(), is(before));
        assertThat(ontology.getAxiomCount(AxiomType.DECLARATION), is(1));
    }

    /**
     * Save As replaces the retained format before saving.  The setting must still apply, and the
     * newly retained format must be left alone too.
     */
    @Test
    public void shouldHonourTheSettingAfterSaveAsChangesTheFormat() throws Exception {
        manager.setOntologyFormat(ontology, new TurtleDocumentFormat());

        String output = saveAsIfByModelManager(true);

        assertThat(DeclarationBaseline.declarationsOf(output), not(hasItem("Class " + NS + "Borrowed")));
        assertTrue(retainedFormat().isAddMissingTypes());
    }

    /**
     * Stands in for {@code OWLModelManagerImpl.save}: everything it does to choose a format, without
     * the progress dialog.
     */
    private String saveAsIfByModelManager(boolean keepDeclarationsInDefiningOntology) throws Exception {
        OWLDocumentFormat format = manager.getOntologyFormat(ontology);
        OWLDocumentFormat saveFormat = mapper(keepDeclarationsInDefiningOntology).mapFormat(format);
        StringDocumentTarget target = new StringDocumentTarget();
        ontology.saveOntology(saveFormat, target);
        return target.toString();
    }

    private DeclarationSynthesisFormatMapper mapper(boolean keepDeclarationsInDefiningOntology) {
        return new DeclarationSynthesisFormatMapper(() -> keepDeclarationsInDefiningOntology);
    }

    private OWLDocumentFormat retainedFormat() {
        return manager.getOntologyFormat(ontology);
    }
}
