package org.protege.ai.core;

import org.protege.editor.owl.model.OWLEditorKitHook;

/** Installs the window's registry through Protégé's existing lifecycle. */
public final class AgentToolRegistryInitializer extends OWLEditorKitHook {

    @Override
    public void initialise() {
        AgentToolRegistry.getRegistry(getEditorKit());
    }

    @Override
    public void dispose() {
        // The editor kit owns and disposes the registry stored in its object map.
    }
}
