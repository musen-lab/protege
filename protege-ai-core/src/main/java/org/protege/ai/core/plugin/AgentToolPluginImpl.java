package org.protege.ai.core.plugin;

import org.eclipse.core.runtime.IExtension;
import org.protege.ai.api.AgentTool;
import org.protege.ai.api.AgentToolDefinition;
import org.protege.ai.api.AgentToolFactory;
import org.protege.editor.core.plugin.AbstractProtegePlugin;
import org.protege.editor.core.plugin.ExtensionInstantiator;
import org.protege.editor.owl.OWLEditorKit;

public final class AgentToolPluginImpl<I, O> extends AbstractProtegePlugin<AgentTool<I, O>> implements AgentToolPlugin<I, O> {

    private final OWLEditorKit editorKit;
    private AgentToolFactory<I, O> factory;

    public AgentToolPluginImpl(IExtension extension, OWLEditorKit editorKit) {
        super(extension);
        this.editorKit = editorKit;
    }

    @Override
    public AgentToolDefinition<I, O> getDefinition() {
        try {
            return getFactory().getDefinition();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not instantiate tool factory", e);
        }
    }

    @Override
    public AgentTool<I, O> newInstance() throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        return getFactory().createTool(editorKit);
    }

    private synchronized AgentToolFactory<I, O> getFactory()
            throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        if (factory == null) {
            String className = getPluginProperty("class");
            if (className == null || className.isBlank()) {
                throw new IllegalArgumentException("Tool factory class must not be blank");
            }
            factory = new ExtensionInstantiator<AgentToolFactory<I, O>>(extension).instantiate();
        }
        return factory;
    }
}
