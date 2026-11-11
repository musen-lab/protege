package org.protege.ai.example;

/** Counts asserted content in the active ontology, excluding imports. */
public record OntologyCountsResult(int classCount, int axiomCount) {}
