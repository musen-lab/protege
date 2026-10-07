package org.protege.editor.owl.model.declaration;

import com.google.common.collect.ImmutableList;
import org.protege.editor.core.log.LogBanner;
import org.semanticweb.owlapi.model.OWLOntologyID;
import org.slf4j.Logger;

import javax.annotation.Nonnull;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Writes a declaration report outline to the Protégé application log.
 *
 * @author Josef Hardi
 */
public final class DeclarationReportLogWriter {

    /**
     * The maximum number of individual findings written for each report section.
     */
    public static final int MAX_ROWS_PER_SECTION = 200;

    static final String BANNER_TITLE = "Entity Declaration Check";

    private DeclarationReportLogWriter() {
    }

    /**
     * Writes a declaration check result to the supplied logger.
     *
     * @param logger the logger that receives the rendered report
     * @param checkedOntology the identifier of the root ontology whose import closure was checked
     * @param outline the report outline; its missing-declaration section must have been checked
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if the missing-declaration check was skipped
     */
    public static void write(@Nonnull Logger logger,
                             @Nonnull OWLOntologyID checkedOntology,
                             @Nonnull DeclarationReportOutline outline) {
        checkNotNull(logger);
        checkNotNull(checkedOntology);
        checkNotNull(outline);
        checkArgument(outline.getMissingSection().getStatus().isExecuted(),
                "A result whose missing-declaration check did not run has nothing to log");
        String ontology = OntologyIdFormat.format(checkedOntology);
        if (!outline.hasFindings()) {
            logger.info(nothingToReportLine(outline, ontology));
            return;
        }
        logger.info(LogBanner.start(BANNER_TITLE));
        logger.info("Checked ontology: {}", ontology);
        writeMissingDeclarationsSection(logger, outline);
        writeMisplacedDeclarationsSection(logger, outline);
        logger.info(LogBanner.end());
    }

    @Nonnull
    private static String nothingToReportLine(@Nonnull DeclarationReportOutline outline,
                                              @Nonnull String ontology) {
        DeclarationReportOutline.Section misplaced = outline.getMisplacedSection();
        if (misplaced.getStatus().isExecuted()) {
            return "Entity declaration check: no missing or misplaced entity declarations in " + ontology;
        }
        return "Entity declaration check: no missing entity declarations in " + ontology
                + "; misplaced entity declarations: " + misplaced.getStatusText();
    }

    private static void writeMissingDeclarationsSection(@Nonnull Logger logger,
                                                        @Nonnull DeclarationReportOutline outline) {
        DeclarationReportOutline.Section section = outline.getMissingSection();
        ImmutableList<MissingDeclarationFinding> findings = outline.getMissing();
        logger.info(section.getHeading());
        for (MissingDeclarationFinding finding : shown(findings)) {
            logger.info("  {}  {}", finding.getEntityType().getPrintName(), finding.getEntity().toStringID());
            logger.info("              used in: {}", OntologyIdFormat.formatAll(finding.getReferringOntologies()));
        }
        writeOmitted(logger, findings.size());
    }

    private static void writeMisplacedDeclarationsSection(@Nonnull Logger logger,
                                                          @Nonnull DeclarationReportOutline outline) {
        ImmutableList<MisplacedDeclarationFinding> findings = outline.getMisplaced();
        logger.info(outline.getMisplacedSection().getHeading());
        for (MisplacedDeclarationFinding finding : shown(findings)) {
            logger.info("  {}  {}", finding.getEntityType().getPrintName(), finding.getEntity().toStringID());
            logger.info("    owned by:    {}  (rule: {})", OntologyIdFormat.format(finding.getOwningOntology()),
                    DeclarationReportOutline.ruleLabelOf(finding));
            logger.info("    declared in: {}", OntologyIdFormat.formatAll(finding.getDeclaringOntologies()));
        }
        writeOmitted(logger, findings.size());
    }

    @Nonnull
    private static <T> ImmutableList<T> shown(@Nonnull ImmutableList<T> findings) {
        return findings.subList(0, Math.min(findings.size(), MAX_ROWS_PER_SECTION));
    }

    private static void writeOmitted(@Nonnull Logger logger, int findingCount) {
        if (findingCount > MAX_ROWS_PER_SECTION) {
            logger.info("  ...and {} more",
                    DeclarationReportOutline.formatCount(findingCount - MAX_ROWS_PER_SECTION));
        }
    }
}
