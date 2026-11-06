package org.protege.editor.owl.model;

import org.protege.editor.owl.model.io.RdfJsonParserFactoryWrapper;
import org.protege.editor.owl.model.io.jsonld.ConfigurableJsonLdStorerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.io.OWLParserFactory;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.rio.RioJsonParserFactory;
import org.semanticweb.owlapi.util.PriorityCollection;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * Creates ontology managers configured for Protégé, including its JSON-LD save and load support.
 *
 * Matthew Horridge
 * Stanford Center for Biomedical Informatics Research
 * 4 Sep 2018
 */
public class OntologyManagerFactory {

    /**
     * Creates a concurrent ontology manager with configurable JSON-LD support installed.
     *
     * @return a manager ready to load and save JSON-LD documents
     */
    @Nonnull
    public static OWLOntologyManager createManager() {
        OWLOntologyManager manager = OWLManager.createConcurrentOWLOntologyManager();
        addJsonLdSupport(manager);
        return manager;
    }

    /**
     * Sets up a manager for Protégé's JSON-LD support: JSON-LD is saved with the JSON-LD settings
     * ({@link ConfigurableJsonLdStorerFactory}), and compacted JSON-LD files can be opened without saying
     * their format (see {@link RdfJsonParserFactoryWrapper}). Calling this more than once does not register
     * another storer or wrap the parser again. Use this for every manager that loads or saves files.
     *
     * @param manager the OWL API manager to configure
     */
    public static void addJsonLdSupport(@Nonnull OWLOntologyManager manager) {
        boolean hasStorer = false;
        for (Object storer : manager.getOntologyStorers()) {
            hasStorer |= storer instanceof ConfigurableJsonLdStorerFactory;
        }
        if (!hasStorer) {
            manager.getOntologyStorers().add(new ConfigurableJsonLdStorerFactory());
        }
        wrapRdfJsonParser(manager.getOntologyParsers());
    }

    /**
     * Lets OWL API move on to the JSON-LD parser when the RDF/JSON parser fails on a JSON-LD document
     * (see {@link RdfJsonParserFactoryWrapper}).
     */
    private static void wrapRdfJsonParser(@Nonnull PriorityCollection<OWLParserFactory> parsers) {
        List<OWLParserFactory> rdfJsonParsers = new ArrayList<>();
        for (OWLParserFactory parser : parsers) {
            if (parser instanceof RioJsonParserFactory) {
                rdfJsonParsers.add(parser);
            }
        }
        for (OWLParserFactory parser : rdfJsonParsers) {
            parsers.remove(parser);
            parsers.add(new RdfJsonParserFactoryWrapper(parser));
        }
    }
}
