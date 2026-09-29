package org.protege.editor.owl.model.declaration;

import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.AddImport;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Integration tests for the misplaced declaration that motivated {@link FirstMentionRule}.
 *
 * <p>The main fixture represents a modular project in which {@code sales} imports {@code core}.
 * Both documents use entity IRIs from a shared vocabulary namespace that matches neither ontology
 * IRI. {@code core} uses {@code Widget} first, but {@code sales} declares it. The identifier-based
 * rules cannot select an owner, while the first-mention rule selects {@code core} and allows the
 * checker to report the declaration in {@code sales} as misplaced.
 *
 * <p>These tests exercise the complete declaration checker rather than the rule in isolation. They
 * also verify that a locally used and declared entity is accepted, and that an identifier-based
 * rule retains precedence when it can select an owner.
 */
public class FirstMentionDefect_TestCase {

    private static final String VOCAB = "http://acme.com/vocab#";

    private static final String CORE = "http://acme.com/core";

    private static final String SALES = "http://acme.com/sales";

    private OWLOntologyManager manager;

    private OWLDataFactory dataFactory;

    @Before
    public void setUp() {
        manager = OWLManager.createOWLOntologyManager();
        dataFactory = manager.getOWLDataFactory();
    }

    @Test
    public void shouldFlagATermDeclaredALayerBelowTheOneUsingIt() throws Exception {
        OWLOntology sales = modularProject();

        MisplacedDeclarationReport report = checkerWith(OwnershipRules.registered()).check(sales);

        assertEquals(ImmutableList.of(VOCAB + "Widget"), names(report));
        assertEquals(FirstMentionRule.ID, report.getFindings().get(0).getOwnershipRuleId());
        assertEquals(IRI.create(CORE),
                report.getFindings().get(0).getOwningOntology().getOntologyIRI().get());
    }

    @Test
    public void shouldReportNothingWithoutTheFirstMentionRule() throws Exception {
        OWLOntology sales = modularProject();

        // Neither naming rule can place a term whose namespace belongs to no document, so without
        // this rule the whole project is invisible to the check.
        assertTrue(checkerWith(ImmutableList.of(new OboIdentifierRule(), new NamespaceRule()))
                .check(sales).isEmpty());
    }

    @Test
    public void shouldReportNothingForATermUsedAndDeclaredInOnePlace() throws Exception {
        OWLOntology core = ontology(CORE);
        OWLOntology sales = ontology(SALES);
        importInto(sales, core);
        OWLClass local = clazz("SalesOnly");
        manager.addAxiom(sales, dataFactory.getOWLDeclarationAxiom(local));
        manager.addAxiom(sales, dataFactory.getOWLSubClassOfAxiom(local, clazz("Thing")));

        assertTrue(checkerWith(OwnershipRules.registered()).check(sales).isEmpty());
    }

    @Test
    public void shouldLeaveATermANamingRulePlacesToThatRule() throws Exception {
        OWLOntology go = ontology("http://purl.obolibrary.org/obo/go.owl");
        OWLOntology sales = ontology(SALES);
        importInto(sales, go);
        OWLClass goTerm = dataFactory.getOWLClass(
                IRI.create("http://purl.obolibrary.org/obo/GO_0006915"));
        manager.addAxiom(go, dataFactory.getOWLSubClassOfAxiom(goTerm, clazz("Process")));
        manager.addAxiom(sales, dataFactory.getOWLDeclarationAxiom(goTerm));

        MisplacedDeclarationReport report = checkerWith(OwnershipRules.registered()).check(sales);

        // The OBO rule reaches the same owner first, so the finding is recorded against it.
        assertEquals(ImmutableList.of("http://purl.obolibrary.org/obo/GO_0006915"), names(report));
        assertEquals(OboIdentifierRule.ID, report.getFindings().get(0).getOwnershipRuleId());
    }

    /**
     * Builds the modular project in which {@code Widget} is used in {@code core} but declared in
     * the importing {@code sales} ontology.
     *
     * @return {@code sales}, the root ontology from which the project is checked
     */
    private OWLOntology modularProject() throws Exception {
        OWLOntology core = ontology(CORE);
        OWLOntology sales = ontology(SALES);
        importInto(sales, core);
        manager.addAxiom(core, dataFactory.getOWLDeclarationAxiom(clazz("Product")));
        manager.addAxiom(core, dataFactory.getOWLSubClassOfAxiom(clazz("Widget"), clazz("Product")));
        manager.addAxiom(sales, dataFactory.getOWLDeclarationAxiom(clazz("Widget")));
        return sales;
    }

    private static MisplacedDeclarationChecker checkerWith(ImmutableList<OwnershipRule> rules) {
        return new MisplacedDeclarationChecker(() -> rules);
    }

    private static List<String> names(MisplacedDeclarationReport report) {
        return report.getFindings().stream()
                .map(finding -> finding.getEntity().getIRI().toString())
                .collect(Collectors.toList());
    }

    private void importInto(OWLOntology importer, OWLOntology imported) {
        manager.applyChange(new AddImport(importer,
                dataFactory.getOWLImportsDeclaration(imported.getOntologyID().getOntologyIRI().get())));
    }

    private OWLOntology ontology(String iri) throws Exception {
        return manager.createOntology(IRI.create(iri));
    }

    private OWLClass clazz(String localName) {
        return dataFactory.getOWLClass(IRI.create(VOCAB + localName));
    }
}
