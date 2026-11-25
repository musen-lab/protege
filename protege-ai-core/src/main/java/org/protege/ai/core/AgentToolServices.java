package org.protege.ai.core;

import org.protege.ai.api.JsonToolService;
import org.protege.ai.core.plugin.AgentToolPluginLoader;
import org.protege.editor.owl.OWLEditorKit;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Desktop wiring for the registry and JSON service belonging to one ontology window. */
public final class AgentToolServices {
    private static final String KEY = "org.protege.ai.core.AgentToolServices";
    private static final Object CREATION_LOCK = new Object();

    private AgentToolServices() {}

    /** Call on Swing's thread, normally from a hook's initialise(), and retain the reference. */
    public static AgentToolRegistry getRegistry(OWLEditorKit editorKit) {
        return getServices(editorKit).registry();
    }

    /** Call on Swing's thread, normally from a hook's initialise(), and retain the reference. */
    public static JsonToolService getJsonTools(OWLEditorKit editorKit) {
        return getServices(editorKit);
    }

    private static AgentToolJsonService getServices(OWLEditorKit editorKit) {
        Objects.requireNonNull(editorKit);
        // The editor kit forwards put/get to the model manager's unsynchronized map, used on Swing's thread.
        synchronized (CREATION_LOCK) {
            var existing = editorKit.get(KEY);
            if (existing instanceof AgentToolJsonService services) {
                return services;
            }
            var services = new AgentToolJsonService(List.of(), editorKit.getOWLModelManager().getOWLDataFactory());
            editorKit.put(KEY, services);
            new AgentToolPluginLoader(editorKit).getPlugins().stream()
                    .sorted(Comparator.comparing(plugin -> plugin.getId()))
                    .forEach(services.registry()::register);
            return services;
        }
    }
}
