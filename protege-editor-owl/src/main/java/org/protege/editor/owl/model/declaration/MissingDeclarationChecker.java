package org.protege.editor.owl.model.declaration;

import org.semanticweb.owlapi.model.OWLOntology;

import javax.annotation.Nonnull;
import java.util.Comparator;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Finds entities that are used by an ontology but are not declared in its import closure.
 *
 * <p>OWL expects classes, properties, and datatypes to be declared. Protege normally adds missing
 * entity declarations automatically when an ontology is saved, which can hide undeclared entities
 * that were already present in the source ontology.
 *
 * <p>When automatic declaration generation is disabled under
 * <b>Preferences &gt; Axioms &gt; Suppress automatic entity declarations when saving</b>,
 * these missing declarations remain visible in the saved ontology. This checker identifies them
 * before saving.
 *
 * <p>An entity is considered declared if a declaration is present either in the ontology itself
 * or in any ontology in its import closure. Only entities from the builtin vocabularies are
 * exempt: every other entity the closure uses must be declared somewhere within it, whoever
 * publishes the vocabulary it belongs to.
 *
 * <p>This check is read-only and does not modify the ontology.
 *
 * @author Josef Hardi
 */
public class MissingDeclarationChecker {

    /**
     * Orders findings by entity IRI and then by entity type to provide deterministic results.
     */
    private static final Comparator<MissingDeclarationFinding> BY_NAME_THEN_KIND =
            Comparator.comparing((MissingDeclarationFinding finding) -> finding.getEntity().getIRI().toString())
                    .thenComparing(finding -> finding.getEntityType().getName());

    /**
     * Finds entities used by the specified ontology that are not declared in its import closure.
     *
     * <p>Entities from the builtin vocabularies are not reported.
     *
     * @param ontology the ontology to check
     * @return a report containing the missing declarations in deterministic order; an empty report
     *         if no missing declarations are found
     */
    @Nonnull
    public MissingDeclarationReport check(@Nonnull OWLOntology ontology) {
        checkNotNull(ontology);
        return check(DeclarationIndex.over(ontology));
    }

    /**
     * Finds missing declarations using an index that has already been built.
     *
     * <p>Sharing an index lets this check and the misplaced-declaration check run over one
     * traversal of the import closure.
     *
     * @param index the index of the import closure to check
     * @return a report containing the missing declarations in deterministic order
     */
    @Nonnull
    MissingDeclarationReport check(@Nonnull DeclarationIndex index) {
        checkNotNull(index);
        return MissingDeclarationReport.get(index.getEntities().stream()
                .filter(entity -> !StandardVocabulary.contains(entity))
                .filter(entity -> !index.isDeclared(entity))
                .map(entity -> MissingDeclarationFinding.get(entity, index.getUsingOntologies(entity)))
                .sorted(BY_NAME_THEN_KIND)
                .collect(Collectors.toList()));
    }
}
