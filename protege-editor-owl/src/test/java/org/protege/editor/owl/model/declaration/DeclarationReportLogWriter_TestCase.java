package org.protege.editor.owl.model.declaration;

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
        assertEquals("Missing entity declarations: 12", lines.get(2));
        assertEquals("  Class  http://example.org/pizza#Class0", lines.get(3));
        assertEquals("              used in: http://example.org/pizza", lines.get(4));
        // Twelve findings of two lines each follow the missing heading.
        assertEquals("  Named individual  http://example.org/pizza#individual2", lines.get(25));
        assertEquals("Misplaced entity declarations: 3", lines.get(27));
        assertEquals("  Class  http://purl.obolibrary.org/obo/GO_0000000", lines.get(28));
        assertEquals("    owned by:    http://purl.obolibrary.org/obo/go.owl  (rule: "
                + new OboIdentifierRule().getDisplay().getLabel() + ")", lines.get(29));
        assertEquals("    declared in: http://example.org/pizza", lines.get(30));
        // Three findings of three lines each follow the misplaced heading, then the banner closes.
        assertEquals(LogBanner.end(), lines.get(37));
        assertEquals(38, lines.size());
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

        assertEquals("Missing entity declarations: 0", lines().get(2));
        assertEquals("Misplaced entity declarations: 3", lines().get(3));
    }

    @Test
    public void shouldHeadAMisplacedCheckThatDidNotRunAsDisabled() {
        write(reportWithMisplacedDisabled(1, 0));

        List<String> lines = lines();
        assertEquals("Missing entity declarations: 1", lines.get(2));
        assertEquals("Misplaced entity declarations: <detection disabled>", lines.get(5));
    }

    @Test
    public void shouldCapASectionAndSayHowManyFindingsWereLeftOut() {
        write(report(2043, 0, 0));

        List<String> lines = lines();
        assertEquals("Missing entity declarations: 2,043", lines.get(2));
        long rows = lines.stream().filter(line -> line.contains("used in:")).count();
        assertEquals(DeclarationReportLogWriter.MAX_ROWS_PER_SECTION, rows);
        assertEquals("  Class  http://example.org/pizza#Class199", lines.get(2 + 2 * 199 + 1));
        assertEquals("  ...and 1,843 more", lines.get(2 + 2 * 200 + 1));
        assertEquals("Misplaced entity declarations: 0", lines.get(2 + 2 * 200 + 2));
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

    private void write(DeclarationReport report) {
        DeclarationReportLogWriter.write(logger, PIZZA, DeclarationReportOutline.of(report));
    }

    private List<String> lines() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).collect(Collectors.toList());
    }
}
