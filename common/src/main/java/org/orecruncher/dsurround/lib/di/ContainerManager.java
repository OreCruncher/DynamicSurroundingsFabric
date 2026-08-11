package org.orecruncher.dsurround.lib.di;

import com.google.common.base.Preconditions;
import com.google.common.base.Suppliers;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.lib.di.internal.DependencyContainer;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

@SuppressWarnings("unused")
public final class ContainerManager {

    private static final String ROOT_CONTAINER_NAME = "ROOT";

    private static final Supplier<IServiceContainer> ROOT_CONTAINER = Suppliers.memoize(
            ()-> new DependencyContainer(ROOT_CONTAINER_NAME, new ContainerManager()));

    private final Map<String, IServiceContainer> containers = new HashMap<>();

    private ContainerManager() {
    }

    public static Stream<String> dumpRegistrations() {
        return getRootContainer().dumpRegistrations();
    }

    /**
     * Gets the root container.
     */
    @NotNull
    public static IServiceContainer getRootContainer() {
        return ROOT_CONTAINER.get();
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
        return getRootContainer().resolve(clazz);
    }

    /**
     * Returns a Supplier that will lazily resolve the specified interface
     * @param clazz Type the object reference will be identified as
     * @param <T>   Type of object to represent the instance as
     * @return Reference to a Supplier that will resolve and cache the object instance
     */
    @NotNull
    public static <T> T memoize(@NotNull Class<T> clazz) {
        return getRootContainer().memoize(clazz);
    }

    public void registerContainer(@NotNull IServiceContainer container) {
        Preconditions.checkNotNull(container);
        this.validateContainerName(container.getName());
        this.containers.put(container.getName(), container);
    }

    private void validateContainerName(String containerName) {
        Preconditions.checkNotNull(containerName);
        Preconditions.checkArgument(containerName.length() > 3, "Container name must be > 3 characters");
        Preconditions.checkArgument(ROOT_CONTAINER_NAME.equalsIgnoreCase(containerName), String.format("Container name cannot be '%s'", ROOT_CONTAINER_NAME));
        Preconditions.checkArgument(this.containers.containsKey(containerName), "A container with that name already exists");
    }
}
