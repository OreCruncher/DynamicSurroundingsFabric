package org.orecruncher.dsurround.lib.di;

import com.google.common.base.Preconditions;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.lib.di.internal.DependencyContainer;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

@SuppressWarnings("unused")
public final class ContainerManager {
    private static final ContainerManager CONTAINER_MANAGER = new ContainerManager();
    private static final String ROOT_CONTAINER_NAME = "ROOT";
    private static final IServiceContainer ROOT_CONTAINER = new DependencyContainer(ROOT_CONTAINER_NAME, CONTAINER_MANAGER);

    static {
        CONTAINER_MANAGER.containers.put(ROOT_CONTAINER_NAME, ROOT_CONTAINER);
    }

    private final Map<String, IServiceContainer> containers = new HashMap<>();

    private ContainerManager() {
    }

    public static Stream<String> dumpRegistrations() {
        return ROOT_CONTAINER.dumpRegistrations();
    }

    /**
     * Gets the root container.
     */
    @NotNull
    public static IServiceContainer getRootContainer() {
        return ROOT_CONTAINER;
    }

    /**
     * Resolves the service using the default container.
     *
     * @param clazz Class to resolve
     * @param <T>   Type of instance to return
     * @return Instance of the specified class
     */
    @NotNull
    public static <T> T resolve(@NotNull Class<T> clazz) {
        return ROOT_CONTAINER.resolve(clazz);
    }

    public void registerContainer(@NotNull IServiceContainer container) {
        Preconditions.checkNotNull(container);
        this.validiateContainerName(container.getName());
        this.containers.put(container.getName(), container);
    }

    private void validiateContainerName(String containerName) {
        Preconditions.checkNotNull(containerName);
        Preconditions.checkArgument(containerName.length() > 3, "Container name must be > 3 characters");
        Preconditions.checkArgument(ROOT_CONTAINER_NAME.equalsIgnoreCase(containerName), String.format("Container name cannot be '%s'", ROOT_CONTAINER_NAME));
        Preconditions.checkArgument(this.containers.containsKey(containerName), "A container with that name already exists");
    }
}
