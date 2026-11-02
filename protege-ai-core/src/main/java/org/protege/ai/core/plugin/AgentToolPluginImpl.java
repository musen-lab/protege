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

    public AgentToolPluginImpl(IExtension extension, OWLEditorKit editorKit) {
        super(extension);
        this.editorKit = editorKit;
    }

    @Override
    public AgentToolDescription getDescription() {
        return new AgentToolDescription(getPluginProperty("name"), getPluginProperty("description"),
                getPluginProperty("inputSchema"), getPluginProperty("resultFormat"));
    }

    @Override
    public AgentTool newInstance() throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        AgentToolFactory factory = new ExtensionInstantiator<AgentToolFactory>(extension).instantiate();
        return factory.createTool(editorKit);
    }
}
