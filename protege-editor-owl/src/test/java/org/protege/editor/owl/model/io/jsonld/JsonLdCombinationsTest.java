package org.protege.editor.owl.model.io.jsonld;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.jsonldjava.core.JsonLdProcessor;
import com.github.jsonldjava.utils.JsonUtils;
import org.junit.BeforeClass;
import org.junit.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.RDFJsonLDDocumentFormat;
import org.semanticweb.owlapi.io.StreamDocumentTarget;
import org.semanticweb.owlapi.io.StringDocumentSource;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.Imports;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BiConsumer;

import static org.junit.Assert.assertTrue;

/**
 * Saves a test ontology with every pair of setting values (all other settings at their defaults) and checks
 * that each file is valid JSON-LD and reopens with nothing lost.
 */
public class JsonLdCombinationsTest {

    private static final String NS = "http://example.org/zoo#";

    private static final IRI IMPORTED = IRI.create("http://example.org/imported");

    private static OWLOntology ontology;

    private static ConfigurableJsonLDDocumentFormat format;

    /** One setting, the values to try, and how to set a value. */
    private static final class Setting {
        final String name;
        final List<Object> values;
        final BiConsumer<JsonLdPreferences, Object> setter;

        Setting(String name, List<?> values, BiConsumer<JsonLdPreferences, Object> setter) {
            this.name = name;
            this.values = new ArrayList<>(values);
            this.setter = setter;
        }
    }

    private static final List<Boolean> BOOLEANS = Arrays.asList(true, false);

    private static final List<Setting> SETTINGS = Arrays.asList(
            new Setting("includeOntologyPrefixes", BOOLEANS, (p, v) -> p.setIncludeOntologyPrefixes((Boolean) v)),
            new Setting("pruneUnusedPrefixes", BOOLEANS, (p, v) -> p.setPruneUnusedPrefixes((Boolean) v)),
            new Setting("useVocab", BOOLEANS, (p, v) -> p.setUseVocab((Boolean) v)),
            new Setting("generateTermsFor", Arrays.asList(GenerateTermsFor.values()), (p, v) -> p.setGenerateTermsFor((GenerateTermsFor) v)),
            new Setting("termNameSource", Arrays.asList(TermNameSource.values()), (p, v) -> p.setTermNameSource((TermNameSource) v)),
            new Setting("termCollisionPolicy", Arrays.asList(TermCollisionPolicy.values()), (p, v) -> p.setTermCollisionPolicy((TermCollisionPolicy) v)),
            new Setting("documentForm", Arrays.asList(DocumentForm.values()), (p, v) -> p.setDocumentForm((DocumentForm) v)),
            new Setting("compactArrays", BOOLEANS, (p, v) -> p.setCompactArrays((Boolean) v)),
            new Setting("keyOrdering", Arrays.asList(KeyOrdering.values()), (p, v) -> p.setKeyOrdering((KeyOrdering) v)),
            new Setting("prettyPrint", BOOLEANS, (p, v) -> p.setPrettyPrint((Boolean) v)),
            new Setting("indentSpaces", Arrays.asList(1, 2, 8), (p, v) -> p.setIndentSpaces((Integer) v)),
            new Setting("useNativeTypes", BOOLEANS, (p, v) -> p.setUseNativeTypes((Boolean) v)),
            new Setting("typeCoercionMode", Arrays.asList(TypeCoercionMode.values()), (p, v) -> p.setTypeCoercionMode((TypeCoercionMode) v)),
            new Setting("useRdfType", BOOLEANS, (p, v) -> p.setUseRdfType((Boolean) v)),
            new Setting("useLanguageMaps", BOOLEANS, (p, v) -> p.setUseLanguageMaps((Boolean) v)),
            new Setting("blankNodeEmbedding", Arrays.asList(BlankNodeEmbedding.values()), (p, v) -> p.setBlankNodeEmbedding((BlankNodeEmbedding) v)),
            new Setting("includeImportsClosure", BOOLEANS, (p, v) -> p.setIncludeImportsClosure((Boolean) v))
    );

    @BeforeClass
    public static void createOntology() throws Exception {
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = manager.getOWLDataFactory();
        ontology = manager.createOntology(IRI.create("http://example.org/zoo"));
        OWLOntology imported = manager.createOntology(IMPORTED);

        OWLClass cat = df.getOWLClass(IRI.create(NS + "Cat"));
        OWLClass animal = df.getOWLClass(IRI.create(NS + "Animal"));
        OWLClass dog = df.getOWLClass(IRI.create(NS + "Dog"));
        OWLClass bird = df.getOWLClass(IRI.create(NS + "Bird"));
        OWLObjectProperty eats = df.getOWLObjectProperty(IRI.create(NS + "eats"));
        OWLDataProperty age = df.getOWLDataProperty(IRI.create(NS + "age"));
        OWLDataProperty tame = df.getOWLDataProperty(IRI.create(NS + "tame"));
        OWLObjectProperty ex = df.getOWLObjectProperty(IRI.create("http://other.org/ns#ex"));
        OWLAnnotationProperty aName = df.getOWLAnnotationProperty(IRI.create("http://a.org/ns#name"));
        OWLAnnotationProperty bName = df.getOWLAnnotationProperty(IRI.create("http://b.org/ns#name"));
        OWLNamedIndividual tom = df.getOWLNamedIndividual(IRI.create(NS + "tom"));

        List<OWLAxiom> axioms = new ArrayList<>(Arrays.asList(
                df.getOWLDeclarationAxiom(cat),
                df.getOWLDeclarationAxiom(eats),
                df.getOWLDeclarationAxiom(age),
                df.getOWLDeclarationAxiom(tame),
                df.getOWLDeclarationAxiom(ex),
                df.getOWLSubClassOfAxiom(cat, df.getOWLObjectSomeValuesFrom(eats, df.getOWLObjectUnionOf(animal, dog))),
                df.getOWLSubClassOfAxiom(cat, df.getOWLObjectAllValuesFrom(eats, animal)),
                df.getOWLClassAssertionAxiom(cat, tom),
                df.getOWLObjectPropertyAssertionAxiom(eats, tom, tom),
                df.getOWLObjectPropertyAssertionAxiom(ex, tom, tom),
                df.getOWLDataPropertyAssertionAxiom(age, tom, df.getOWLLiteral(30)),
                df.getOWLDataPropertyAssertionAxiom(tame, tom, df.getOWLLiteral(true)),
                annotation(df, cat.getIRI(), df.getRDFSLabel(), df.getOWLLiteral("Cat", "en")),
                annotation(df, cat.getIRI(), df.getRDFSLabel(), df.getOWLLiteral("Chat", "fr")),
                annotation(df, cat.getIRI(), df.getRDFSComment(), df.getOWLLiteral("A cat", "en")),
                annotation(df, cat.getIRI(), df.getRDFSComment(), df.getOWLLiteral("Feline", "en")),
                // rdfs:comment also has an untagged value, so not all of its values fit a language map
                annotation(df, dog.getIRI(), df.getRDFSComment(), df.getOWLLiteral("A dog, no language tag")),
                // An annotation on an axiom
                df.getOWLSubClassOfAxiom(dog, animal, java.util.Collections.singleton(
                        df.getOWLAnnotation(df.getRDFSLabel(), df.getOWLLiteral("dogs are animals")))),
                annotation(df, age.getIRI(), df.getRDFSLabel(), df.getOWLLiteral("has age", "en")),
                // Two annotation properties with the same local name "name"
                annotation(df, tom.getIRI(), aName, df.getOWLLiteral("Tom")),
                annotation(df, tom.getIRI(), bName, df.getOWLLiteral("Thomas"))
        ));
        manager.addAxioms(ontology, new HashSet<>(axioms));
        manager.applyChange(new AddOntologyAnnotation(ontology, df.getOWLAnnotation(df.getRDFSComment(), df.getOWLLiteral("Test ontology"))));
        manager.addAxiom(imported, df.getOWLSubClassOfAxiom(bird, animal));
        manager.applyChange(new AddImport(ontology, df.getOWLImportsDeclaration(IMPORTED)));

        format = new ConfigurableJsonLDDocumentFormat();
        format.setPrefix("ex:", NS);
        format.setPrefix("other:", "http://other.org/ns#");
        format.setPrefix("a:", "http://a.org/ns#");
        format.setPrefix("b:", "http://b.org/ns#");
        format.setPrefix("owl:", "http://www.w3.org/2002/07/owl#");
        format.setPrefix("rdf:", "http://www.w3.org/1999/02/22-rdf-syntax-ns#");
        format.setPrefix("rdfs:", "http://www.w3.org/2000/01/rdf-schema#");
        format.setPrefix("xml:", "http://www.w3.org/XML/1998/namespace");
        format.setPrefix("xsd:", "http://www.w3.org/2001/XMLSchema#");
    }

    private static OWLAxiom annotation(OWLDataFactory df, IRI subject, OWLAnnotationProperty property, OWLAnnotationValue value) {
        return df.getOWLAnnotationAssertionAxiom(subject, df.getOWLAnnotation(property, value));
    }

    @Test
    public void everyPairOfSettingValuesSavesAndReopensWithNothingLost() {
        List<String> failures = new ArrayList<>();
        int combinations = 0;
        for (int i = 0; i < SETTINGS.size(); i++) {
            for (int j = i + 1; j < SETTINGS.size(); j++) {
                Setting first = SETTINGS.get(i);
                Setting second = SETTINGS.get(j);
                for (Object a : first.values) {
                    for (Object b : second.values) {
                        JsonLdPreferences prefs = new JsonLdPreferences(InMemoryPreferences::new);
                        first.setter.accept(prefs, a);
                        second.setter.accept(prefs, b);
                        String label = first.name + "=" + a + ", " + second.name + "=" + b;
                        String problem = check(prefs);
                        if (problem != null) {
                            failures.add(label + ": " + problem);
                        }
                        combinations++;
                    }
                }
            }
        }
        System.out.println("JSON-LD combinations checked: " + combinations);
        assertTrue(failures.size() + " of " + combinations + " combinations failed:\n" + String.join("\n", failures),
                   failures.isEmpty());
    }

    /**
     * Returns null if the combination works, or a description of what went wrong.
     */
    private static String check(JsonLdPreferences prefs) {
        // Expanded files have no @context, so a name clash can't matter there
        boolean clashShouldStopTheSave = prefs.getTermCollisionPolicy() == TermCollisionPolicy.ERROR
                && prefs.getGenerateTermsFor() != GenerateTermsFor.NONE
                && prefs.getDocumentForm() != DocumentForm.EXPANDED;
        String jsonLd;
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            new ConfigurableJsonLdStorer(prefs).storeOntology(ontology, new StreamDocumentTarget(out), format);
            jsonLd = out.toString(StandardCharsets.UTF_8.name());
        } catch (OWLOntologyStorageException e) {
            return clashShouldStopTheSave ? null : "save failed: " + e.getMessage();
        } catch (Exception e) {
            return "save failed: " + e;
        }
        if (clashShouldStopTheSave) {
            return "save should have stopped because of the name clash";
        }
        try {
            new ObjectMapper().readTree(jsonLd);
        } catch (Exception e) {
            return "not valid JSON: " + e.getMessage();
        }
        try {
            JsonLdProcessor.expand(JsonUtils.fromString(jsonLd));
        } catch (Exception e) {
            return "not valid JSON-LD: " + e.getMessage();
        }
        try {
            Set<OWLAxiom> expected = content(ontology, Imports.fromBoolean(prefs.isIncludeImportsClosure()));
            Set<OWLAxiom> actual = content(reload(jsonLd), Imports.EXCLUDED);
            if (!expected.equals(actual)) {
                Set<OWLAxiom> lost = new TreeSet<>(expected);
                lost.removeAll(actual);
                Set<OWLAxiom> extra = new TreeSet<>(actual);
                extra.removeAll(expected);
                return "content differs after reopening; lost " + lost + ", extra " + extra;
            }
        } catch (Exception e) {
            return "reopening failed: " + e;
        }
        return null;
    }

    static OWLOntology reload(String jsonLd) throws OWLOntologyCreationException {
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        // Don't try to download the imported ontology
        manager.setOntologyLoaderConfiguration(manager.getOntologyLoaderConfiguration().addIgnoredImport(IMPORTED));
        return manager.loadOntologyFromOntologyDocument(
                new StringDocumentSource(jsonLd, IRI.create("http://example.org/doc"), new RDFJsonLDDocumentFormat(), null));
    }

    private static Set<OWLAxiom> content(OWLOntology ont, Imports imports) {
        Set<OWLAxiom> axioms = new HashSet<>();
        for (OWLOntology o : imports == Imports.INCLUDED ? ont.getImportsClosure() : Collections.singleton(ont)) {
            axioms.addAll(o.getLogicalAxioms());
            axioms.addAll(o.getAxioms(AxiomType.ANNOTATION_ASSERTION));
        }
        return axioms;
    }
}
