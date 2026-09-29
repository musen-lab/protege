package org.protege.editor.owl.model.declaration;

import com.google.common.collect.ImmutableList;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntologyID;

import java.util.Collections;

/**
 * Factory methods and ontology identifiers shared by declaration-report presentation tests.
 *
 * <p>The generated reports use predictable entity IRIs and preserve a fixed finding order, which
 * lets tests assert rendered counts, truncation, and individual output lines.
 */
public final class DeclarationReportFixtures {

    /**
     * The ontology identifier used as the checked ontology and as the source of entity usage or
     * misplaced declarations.
     */
    public static final OWLOntologyID PIZZA = new OWLOntologyID(IRI.create("http://example.org/pizza"));

    /**
     * The ontology identifier used as the owner of generated Gene Ontology entities.
     */
    public static final OWLOntologyID GO = new OWLOntologyID(IRI.create("http://purl.obolibrary.org/obo/go.owl"));

    private static final OWLDataFactory DF = OWLManager.getOWLDataFactory();

    private DeclarationReportFixtures() {
    }

    /**
     * Creates a report in which both declaration checks ran.
     *
     * @param missingErrors the non-negative number of missing class declarations to create
     * @param missingWarnings the non-negative number of missing individual declarations to create
     * @param misplaced the non-negative number of misplaced class declarations to create
     * @return a checked report containing the requested findings
     */
    public static DeclarationReport report(int missingErrors, int missingWarnings, int misplaced) {
        return DeclarationReport.get(missing(missingErrors, missingWarnings), misplaced(misplaced));
    }

    /**
     * Creates a report in which the missing-declaration check ran but the misplaced-declaration
     * check was skipped because no ownership rule was enabled.
     *
     * @param missingErrors the non-negative number of missing class declarations to create
     * @param missingWarnings the non-negative number of missing individual declarations to create
     * @return a report containing the requested missing findings and a disabled misplaced section
     */
    public static DeclarationReport reportWithMisplacedDisabled(int missingErrors, int missingWarnings) {
        return DeclarationReport.get(missing(missingErrors, missingWarnings),
                MisplacedDeclarationReport.skipped(DeclarationCheckStatus.MISPLACED_SKIPPED));
    }

    /**
     * Creates missing-declaration findings with errors before warnings.
     *
     * <p>Errors are undeclared pizza classes named {@code Class0}, {@code Class1}, and so on.
     * Warnings are undeclared named individuals named {@code individual0}, {@code individual1},
     * and so on. Every entity is used by {@link #PIZZA}.
     *
     * @param errors the non-negative number of class findings to create
     * @param warnings the non-negative number of named-individual findings to create
     * @return a checked missing-declaration report containing the requested findings
     */
    public static MissingDeclarationReport missing(int errors, int warnings) {
        ImmutableList.Builder<MissingDeclarationFinding> findings = ImmutableList.builder();
        for (int i = 0; i < errors; i++) {
            findings.add(MissingDeclarationFinding.get(
                    DF.getOWLClass(IRI.create("http://example.org/pizza#Class" + i)),
                    Collections.singleton(PIZZA)));
        }
        for (int i = 0; i < warnings; i++) {
            findings.add(MissingDeclarationFinding.get(
                    DF.getOWLNamedIndividual(IRI.create("http://example.org/pizza#individual" + i)),
                    Collections.singleton(PIZZA)));
        }
        return MissingDeclarationReport.get(findings.build());
    }

    /**
     * Creates misplaced-declaration findings for sequential Gene Ontology classes.
     *
     * <p>Each class is owned by {@link #GO} according to {@link OboIdentifierRule}, but is declared
     * in {@link #PIZZA}.
     *
     * @param count the non-negative number of misplaced class findings to create
     * @return a checked misplaced-declaration report containing the requested findings
     */
    public static MisplacedDeclarationReport misplaced(int count) {
        ImmutableList.Builder<MisplacedDeclarationFinding> findings = ImmutableList.builder();
        for (int i = 0; i < count; i++) {
            findings.add(MisplacedDeclarationFinding.get(
                    DF.getOWLClass(IRI.create(String.format("http://purl.obolibrary.org/obo/GO_%07d", i))),
                    GO,
                    Collections.singleton(PIZZA),
                    OboIdentifierRule.ID));
        }
        return MisplacedDeclarationReport.get(findings.build());
    }
}
