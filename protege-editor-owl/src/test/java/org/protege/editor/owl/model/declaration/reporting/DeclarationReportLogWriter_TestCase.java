package org.protege.editor.owl.model.declaration.reporting;

import org.protege.editor.owl.model.declaration.DeclarationCheckStatus;
import org.protege.editor.owl.model.declaration.DeclarationReport;
import org.protege.editor.owl.model.declaration.DeclarationSeverity;
import org.protege.editor.owl.model.declaration.MisplacedDeclarationFinding;
import org.protege.editor.owl.model.declaration.MisplacedDeclarationReport;
import org.protege.editor.owl.model.declaration.MissingDeclarationReport;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.protege.editor.core.log.LogBanner;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

import static org.protege.editor.owl.model.declaration.DeclarationReportFixtures.*;
import static org.junit.Assert.*;

/**
 * Tests the lines {@link DeclarationReportLogWriter} writes to the log, their order and their levels.
 */
public class DeclarationReportLogWriter_TestCase {

    private Logger logger;

    private ListAppender<ILoggingEvent> appender;

    @Before
    public void setUp() {
        logger = (Logger) LoggerFactory.getLogger(DeclarationReportLogWriter_TestCase.class);
        logger.setAdditive(false);
        appender = new ListAppender<>();
        appender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        appender.start();
        logger.addAppender(appender);
    }

    @After
    public void tearDown() {
        logger.detachAppender(appender);
        logger.setAdditive(true);
    }

    @Test
    public void shouldWriteTheBannerTheOntologyAndBothSectionsWithTheirFindings() {
        write(report(9, 3, 3));

        List<String> lines = lines();
        assertEquals(LogBanner.start("Entity Declaration Check"), lines.get(0));
        assertEquals("Checked ontology: http://example.org/pizza", lines.get(1));
        assertEquals("Entities that have missing declarations: 12", lines.get(2));
        assertEquals("  Class  http://example.org/pizza#Class0", lines.get(3));
        // Each missing entity occupies one line, followed by the misplaced declaration axioms.
        assertEquals("  Named individual  http://example.org/pizza#individual2", lines.get(14));
        assertEquals("Entity declarations that are misplaced: 3", lines.get(15));
        for (int i = 0; i < 3; i++) {
            assertEquals("  Class  Declaration(http://purl.obolibrary.org/obo/GO_000000" + i + ")",
                    lines.get(16 + i));
        }
        assertEquals(LogBanner.end(), lines.get(19));
        assertEquals(20, lines.size());
    }

    @Test
    public void shouldWriteEveryLineAtInformationLevel() {
        write(report(9, 3, 3));

        List<Level> levels = appender.list.stream().map(ILoggingEvent::getLevel).collect(Collectors.toList());
        // Nothing at WARN or above, so the status bar log icon is left alone.
        assertTrue(levels.stream().allMatch(Level.INFO::equals));
    }

    @Test
    public void shouldHeadASectionThatFoundNothingWithZero() {
        write(report(0, 0, 3));

        assertEquals("Entities that have missing declarations: 0", lines().get(2));
        assertEquals("Entity declarations that are misplaced: 3", lines().get(3));
    }

    @Test
    public void shouldHeadAMisplacedCheckThatDidNotRunAsDisabled() {
        write(reportWithMisplacedDisabled(1, 0));

        List<String> lines = lines();
        assertEquals("Entities that have missing declarations: 1", lines.get(2));
        assertEquals("Entity declarations that are misplaced: <detection disabled>", lines.get(4));
    }

    @Test
    public void shouldCapASectionAndSayHowManyFindingsWereLeftOut() {
        write(report(2043, 0, 0));

        List<String> lines = lines();
        assertEquals("Entities that have missing declarations: 2,043", lines.get(2));
        long rows = lines.stream().filter(line -> line.startsWith("  Class  ")).count();
        assertEquals(DeclarationReportLogWriter.MAX_ROWS_PER_SECTION, rows);
        for (int i = 0; i < DeclarationReportLogWriter.MAX_ROWS_PER_SECTION; i++) {
            assertEquals("  Class  http://example.org/pizza#Class" + i, lines.get(3 + i));
        }
        assertEquals("  ...and 1,843 more", lines.get(203));
        assertEquals("Entity declarations that are misplaced: 0", lines.get(204));
        assertEquals(LogBanner.end(), lines.get(205));
        assertEquals(206, lines.size());
    }

    @Test
    public void shouldNotCapASectionHoldingExactlyTheLimit() {
        write(report(0, 0, DeclarationReportLogWriter.MAX_ROWS_PER_SECTION));

        assertTrue(lines().stream().noneMatch(line -> line.contains("more")));
    }

    @Test
    public void shouldWriteOneInformationLineWhenBothChecksFoundNothing() {
        write(report(0, 0, 0));

        assertEquals(1, appender.list.size());
        assertEquals(Level.INFO, appender.list.get(0).getLevel());
        assertEquals("Entity declaration check: no missing or misplaced entity declarations in "
                + "http://example.org/pizza", lines().get(0));
    }

    @Test
    public void shouldWriteOneInformationLineWhenNothingWasFoundAndMisplacedDetectionIsDisabled() {
        write(reportWithMisplacedDisabled(0, 0));

        assertEquals(1, appender.list.size());
        assertEquals(Level.INFO, appender.list.get(0).getLevel());
        assertEquals("Entity declaration check: no missing entity declarations in http://example.org/pizza; "
                + "misplaced entity declarations: <detection disabled>", lines().get(0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRefuseAResultWhoseChecksDidNotRun() {
        write(DeclarationReport.get(
                MissingDeclarationReport.skipped(DeclarationCheckStatus.SKIPPED),
                MisplacedDeclarationReport.skipped(DeclarationCheckStatus.SKIPPED)));
    }

    @Test
    public void shouldLogOnlyTheDeclarationAxiomWhileRetainingPlacementDiagnosticsInTheFinding() {
        MisplacedDeclarationFinding finding = MisplacedDeclarationFinding.create(
                org.semanticweb.owlapi.apibinding.OWLManager.getOWLDataFactory().getOWLClass(
                        org.semanticweb.owlapi.model.IRI.create("http://example.org/QuickBaking")),
                new org.semanticweb.owlapi.model.OWLOntologyID(
                        org.semanticweb.owlapi.model.IRI.create("http://example.org/pizza-fixture/baking")),
                java.util.Arrays.asList(
                        new org.semanticweb.owlapi.model.OWLOntologyID(org.semanticweb.owlapi.model.IRI.create("http://example.org/pizza-fixture/menu")),
                        new org.semanticweb.owlapi.model.OWLOntologyID(org.semanticweb.owlapi.model.IRI.create("http://example.org/other-menu"))),
                "test-rule");
        write(DeclarationReport.get(MissingDeclarationReport.get(java.util.Collections.emptyList()),
                MisplacedDeclarationReport.get(java.util.Collections.singletonList(finding))));
        assertEquals(java.util.Arrays.asList(
                LogBanner.start("Entity Declaration Check"),
                "Checked ontology: http://example.org/pizza",
                "Entities that have missing declarations: 0",
                "Entity declarations that are misplaced: 1",
                "  Class  Declaration(http://example.org/QuickBaking)",
                LogBanner.end()), lines());
        assertEquals("Suggested placement: http://example.org/pizza-fixture/menu, "
                        + "http://example.org/other-menu → http://example.org/pizza-fixture/baking",
                DeclarationReportOutline.suggestedPlacementOf(finding));
        assertEquals("test-rule", DeclarationReportOutline.ruleLabelOf(finding));
        assertEquals(DeclarationSeverity.WARNING, finding.getSeverity());
    }

    private void write(DeclarationReport report) {
        DeclarationReportLogWriter.write(logger, PIZZA, DeclarationReportOutline.of(report));
    }

    private List<String> lines() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).collect(Collectors.toList());
    }
}
