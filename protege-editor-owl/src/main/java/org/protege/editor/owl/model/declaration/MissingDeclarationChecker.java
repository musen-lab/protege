package org.protege.editor.owl.model.declaration;

import org.semanticweb.owlapi.model.OWLEntity;
import org.semanticweb.owlapi.model.OWLOntology;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Finds entities that are used by an ontology but are not declared in its import closure.
 *
 * <p>OWL expects classes, properties, and datatypes to be declared. Protege normally adds missing
 * entity declarations automatically when an ontology is saved, which can hide undeclared entities
 * that were already present in the source ontology.
 *
 * <p>When automatic declaration generation is disabled under
 * <b>Preferences &gt; General &gt; Suppress automatic entity declarations when saving</b>,
 * these missing declarations remain visible in the saved ontology. This checker identifies them
 * before saving.
 *
 * <p>An entity is considered declared if a declaration is present either in the ontology itself
 * or in any ontology in its import closure. Entities from recognized standard vocabularies are
 * excluded from the report.
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
     * <p>Entities from recognized standard vocabularies are not reported.
     *
     * @param ontology the ontology to check
     * @return a report containing the missing declarations in deterministic order; an empty report
     *         if no missing declarations are found
     */
    @Nonnull
    public MissingDeclarationReport check(@Nonnull OWLOntology ontology) {
        checkNotNull(ontology);
        DeclarationIndex index = DeclarationIndex.over(ontology);
        List<MissingDeclarationFinding> findings = new ArrayList<>();
        for (OWLEntity entity : index.getEntities()) {
            if (StandardVocabulary.contains(entity) || index.isDeclared(entity)) {
                continue;
            }
            findings.add(MissingDeclarationFinding.get(entity, index.getUsingOntologies(entity)));
        }
        findings.sort(BY_NAME_THEN_KIND);
        return MissingDeclarationReport.get(findings);
    }
}
