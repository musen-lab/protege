package org.protege.ai.core.plugin;

import org.eclipse.core.runtime.IExtension;
import org.protege.editor.core.plugin.AbstractPluginLoader;
import org.protege.editor.owl.OWLEditorKit;

public final class AgentToolPluginLoader extends AbstractPluginLoader<AgentToolPlugin> {

    private final OWLEditorKit editorKit;

    public AgentToolPluginLoader(OWLEditorKit editorKit) {
        super(AgentToolPlugin.PLUGIN_ID, AgentToolPlugin.EXTENSION_POINT_ID);
        this.editorKit = editorKit;
    }

    @Override
    protected AgentToolPlugin createInstance(IExtension extension) {
        return new AgentToolPluginImpl(extension, editorKit);
    }
}
