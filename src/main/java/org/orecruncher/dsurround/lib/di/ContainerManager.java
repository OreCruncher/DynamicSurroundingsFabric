package org.orecruncher.dsurround.lib.di;

import org.orecruncher.dsurround.lib.di.internal.DependencyContainer;
import org.orecruncher.dsurround.lib.Lazy;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@SuppressWarnings("unused")
public final class ContainerManager {
    public static final ContainerManager Default = new ContainerManager();
    private static final String DefaultContainerName = "Default";
    private final ConcurrentMap<String, IServiceContainer> _containers = new ConcurrentHashMap<>();

    private static final Lazy<IServiceContainer> DefaultContainer = new Lazy<>(() -> Default._containers.computeIfAbsent(DefaultContainerName, n -> new DependencyContainer(n, Default)));

    private ContainerManager() {
    }

    /**
     * Obtains the default container.  This container is a well named container within the default
     * container manager.
     */
    public static IServiceContainer getDefaultContainer() {
        return DefaultContainer.get();
    }

    /**
     * Resolves the service using the default container.
     *
     * @param clazz Class to resolve
     * @param <T>   Type of instance to return
     * @return Instance of the specified class
     */
    public static <T> T resolve(Class<T> clazz) {
        return getDefaultContainer().resolve(clazz);
    }

    public IServiceContainer createContainer(String containerName) {
        var container = new DependencyContainer(containerName, this);
        this.registerContainer(container);
        return container;
    }

    public void registerContainer(IServiceContainer container) {
        this._containers.put(container.get_name(), container);
    }

    public Optional<IServiceContainer> getContainer(String containerName) {
        return Optional.ofNullable(this._containers.get(containerName));
    }
}
