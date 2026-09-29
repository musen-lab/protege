package org.protege.editor.owl.model.declaration;

/**
 * Describes whether a declaration check was performed and, if it was skipped, what was skipped.
 *
 * <p>A skipped check always produces an empty report. Its status therefore distinguishes it from
 * a check that completed successfully but found no declaration issues. An {@link #EXECUTED} status
 * says only that the check ran; it does not imply that the report contains findings.
 *
 * @author Josef Hardi
 */
public enum DeclarationCheckStatus {

    /**
     * The check completed, whether or not it found any declaration issues.
     */
    EXECUTED,

    /**
     * Both the missing- and misplaced-declaration checks were skipped because the save operation
     * writes missing entity declarations automatically.
     */
    SKIPPED,

    /**
     * Only the misplaced-declaration check was skipped because no entity-ownership rule is
     * enabled. The missing-declaration check still runs, so this status appears only on a
     * misplaced-declaration report.
     */
    MISPLACED_SKIPPED;

    /**
     * Determines whether this status represents a completed check.
     *
     * @return {@code true} if this status is {@link #EXECUTED}; {@code false} if it records what
     *         was skipped
     */
    public boolean isExecuted() {
        return this == EXECUTED;
    }
}
