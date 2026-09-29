package org.protege.editor.owl.ui.declaration;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.protege.editor.owl.model.declaration.DeclarationCheckStatus;
import org.protege.editor.owl.model.declaration.DeclarationReport;
import org.protege.editor.owl.model.declaration.DeclarationReportOutline;
import org.protege.editor.owl.model.declaration.MisplacedDeclarationReport;
import org.protege.editor.owl.model.declaration.MissingDeclarationReport;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLOntology;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;
import static org.protege.editor.owl.model.declaration.DeclarationReportFixtures.report;

/**
 * Tests what a save's declaration check writes to the log and when it warns.
 */
public class DeclarationIssuesReporter_TestCase {

    private Logger logger;

    private ListAppender<ILoggingEvent> appender;

    private final List<DeclarationReportOutline> warnings = new ArrayList<>();

    private final List<OWLOntology> checked = new ArrayList<>();

    private OWLOntology root;

    @Before
    public void setUp() throws Exception {
        logger = (Logger) LoggerFactory.getLogger(DeclarationIssuesReporter.class);
        logger.setAdditive(false);
        appender = new ListAppender<>();
        appender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        appender.start();
        logger.addAppender(appender);
        root = OWLManager.createOWLOntologyManager().createOntology(IRI.create("http://example.org/pizza"));
    }

    @After
    public void tearDown() {
        logger.detachAppender(appender);
        logger.setAdditive(true);
    }

    @Test
    public void shouldLogAndWarnNothingWhileAutomaticDeclarationsAreWritten() {
        reporterReturning(DeclarationReport.get(
                MissingDeclarationReport.skipped(DeclarationCheckStatus.SKIPPED),
                MisplacedDeclarationReport.skipped(DeclarationCheckStatus.SKIPPED)))
                .report(root);

        assertEquals(List.of(root), checked);
        assertTrue(appender.list.isEmpty());
        assertTrue(warnings.isEmpty());
    }

    @Test
    public void shouldLogOneLineAndNotWarnWhenNothingWasFound() {
        reporterReturning(report(0, 0, 0)).report(root);

        assertEquals(1, appender.list.size());
        assertEquals(Level.INFO, appender.list.get(0).getLevel());
        assertTrue(appender.list.get(0).getFormattedMessage().endsWith("in http://example.org/pizza"));
        assertTrue(warnings.isEmpty());
    }

    @Test
    public void shouldLogTheReportAndWarnOnceWhenThereAreFindings() {
        reporterReturning(report(9, 3, 3)).report(root);

        assertEquals("Checked ontology: http://example.org/pizza",
                appender.list.get(1).getFormattedMessage());
        assertEquals(1, warnings.size());
        assertEquals("Missing entity declarations: 12", warnings.get(0).getMissingSection().getHeading());
    }

    @Test
    public void shouldLogAFailedCheckWithoutWarning() {
        new DeclarationIssuesReporter(ontology -> {
            throw new IllegalStateException("boom");
        }, warnings::add).report(root);

        assertEquals(1, appender.list.size());
        assertEquals(Level.ERROR, appender.list.get(0).getLevel());
        assertTrue(warnings.isEmpty());
    }

    private DeclarationIssuesReporter reporterReturning(DeclarationReport result) {
        return new DeclarationIssuesReporter(ontology -> {
            checked.add(ontology);
            return result;
        }, warnings::add);
    }
}
