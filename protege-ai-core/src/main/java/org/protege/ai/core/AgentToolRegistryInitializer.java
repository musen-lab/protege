package org.protege.ai.core;

import org.protege.editor.owl.model.OWLEditorKitHook;

/** Installs the window's registry through Protégé's existing lifecycle. */
public final class AgentToolRegistryInitializer extends OWLEditorKitHook {

    @Override
    public void initialise() {
        AgentToolServices.getRegistry(getEditorKit());
    }

    @Override
    public void dispose() {
        // The editor kit owns the services; their disposal closes the registry.
    }
}
