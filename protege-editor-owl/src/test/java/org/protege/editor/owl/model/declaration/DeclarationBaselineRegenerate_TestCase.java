package org.protege.editor.owl.model.declaration;

import org.junit.Test;

import static org.junit.Assume.assumeTrue;

/**
 * Regenerates the declaration baselines.  Skipped unless asked for:
 *
 * <pre>
 * mvn test -pl protege-editor-owl -am -Dtest=DeclarationBaselineRegenerate_TestCase \
 *     -Ddeclarations.regenerate=true -Dsurefire.failIfNoSpecifiedTests=false
 * </pre>
 *
 * <p>Read {@code src/test/resources/declarations/README.md} first.  Regenerating is right for a
 * deliberate OWL API upgrade and wrong as a way to make a red test go green.
 */
public class DeclarationBaselineRegenerate_TestCase {

    private static final String REGENERATE_PROPERTY = "declarations.regenerate";

    @Test
    public void shouldRegenerateBaselinesWhenAsked() throws Exception {
        assumeTrue("Set -D" + REGENERATE_PROPERTY + "=true to regenerate the declaration baselines",
                Boolean.getBoolean(REGENERATE_PROPERTY));
        DeclarationBaseline.main(new String[0]);
    }
}
