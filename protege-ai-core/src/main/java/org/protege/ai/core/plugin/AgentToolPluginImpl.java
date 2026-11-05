package org.protege.ai.core.plugin;

import org.eclipse.core.runtime.IExtension;
import org.protege.ai.api.AgentTool;
import org.protege.ai.api.AgentToolDescription;
import org.protege.ai.api.AgentToolFactory;
import org.protege.editor.core.plugin.AbstractProtegePlugin;
import org.protege.editor.core.plugin.ExtensionInstantiator;
import org.protege.editor.owl.OWLEditorKit;

public final class AgentToolPluginImpl extends AbstractProtegePlugin<AgentTool> implements AgentToolPlugin {

    private final OWLEditorKit editorKit;
    private AgentToolFactory factory;

    public AgentToolPluginImpl(IExtension extension, OWLEditorKit editorKit) {
        super(extension);
        this.editorKit = editorKit;
    }

    @Override
    public AgentToolDescription getDescription() {
        try {
            return getFactory().getDescription();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not instantiate tool factory", e);
        }
    }

    @Override
    public AgentTool newInstance() throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        return getFactory().createTool(editorKit);
    }

    private synchronized AgentToolFactory getFactory()
            throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        if (factory == null) {
            String className = getPluginProperty("class");
            if (className == null || className.isBlank()) {
                throw new IllegalArgumentException("Tool factory class must not be blank");
            }
            factory = new ExtensionInstantiator<AgentToolFactory>(extension).instantiate();
        }
        return factory;
    }
}
