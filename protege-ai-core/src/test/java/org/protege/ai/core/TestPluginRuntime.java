package org.protege.ai.core;

import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IContributor;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;
import org.osgi.service.packageadmin.PackageAdmin;
import org.protege.editor.core.plugin.PluginUtilities;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Exercises the normal loader and class instantiation without starting OSGi. */
final class TestPluginRuntime implements AutoCloseable {

    TestPluginRuntime(IExtension... extensions) throws Exception {
        BundleContext context = mock(BundleContext.class);
        when(context.createFilter(anyString())).thenAnswer(call -> FrameworkUtil.createFilter(call.getArgument(0)));
        IExtensionRegistry registry = mock(IExtensionRegistry.class);
        IExtensionPoint point = mock(IExtensionPoint.class);
        when(registry.getExtensionPoint("org.protege.ai.core", "AgentTool")).thenReturn(point);
        when(point.getExtensions()).thenReturn(extensions);
        service(context, IExtensionRegistry.class, registry);

        PackageAdmin packages = mock(PackageAdmin.class);
        Bundle bundle = mock(Bundle.class);
        when(packages.getBundles(eq("test.plugin"), isNull())).thenReturn(new Bundle[]{bundle});
        when(bundle.loadClass(anyString())).thenAnswer(call -> Class.forName(call.getArgument(0)));
        service(context, PackageAdmin.class, packages);
        PluginUtilities.getInstance().initialise(context);
    }

    private <T> void service(BundleContext context, Class<T> type, T instance) throws Exception {
        @SuppressWarnings("unchecked")
        ServiceReference<T> reference = mock(ServiceReference.class);
        when(context.getServiceReferences(eq(type.getName()), isNull()))
                .thenReturn(new ServiceReference<?>[]{reference});
        when(context.getService(reference)).thenReturn(instance);
    }

    static IExtension extension(String id, String factoryClass) {
        IExtension extension = mock(IExtension.class);
        IContributor contributor = mock(IContributor.class);
        when(contributor.getName()).thenReturn("test.plugin");
        when(extension.getContributor()).thenReturn(contributor);
        when(extension.getUniqueIdentifier()).thenReturn(id);
        IConfigurationElement element = mock(IConfigurationElement.class);
        when(element.getName()).thenReturn("class");
        when(element.getAttribute("value")).thenReturn(factoryClass);
        when(extension.getConfigurationElements()).thenReturn(new IConfigurationElement[]{element});
        return extension;
    }

    @Override
    public void close() {
        PluginUtilities.getInstance().dispose();
    }
}
