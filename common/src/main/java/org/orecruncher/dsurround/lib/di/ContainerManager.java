package org.orecruncher.dsurround.lib.di;

import com.google.common.base.Suppliers;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.lib.di.internal.DependencyContainer;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Access to the mod's container.
 */
public final class ContainerManager {

    private static final String ROOT_CONTAINER_NAME = "ROOT";

    private static final Supplier<IServiceContainer> ROOT_CONTAINER = Suppliers.memoize(
            () -> new DependencyContainer(ROOT_CONTAINER_NAME));

    private ContainerManager() {
    }

    public static Stream<String> dumpRegistrations() {
        return getRootContainer().dumpRegistrations();
    }

    /**
     * Checks the root container's registrations. See {@link IServiceContainer#validate}.
     */
    public static List<String> validate(Class<?>... additionalRoots) {
        return getRootContainer().validate(additionalRoots);
    }

    /**
     * Gets the root container.
     */
    @NotNull
    public static IServiceContainer getRootContainer() {
        return ROOT_CONTAINER.get();
    }

    /**
     * Resolves the type using the root container. See {@link IServiceContainer#resolve}.
     */
    @NotNull
    public static <T> T resolve(@NotNull Class<T> clazz) {
        return getRootContainer().resolve(clazz);
    }

    /**
     * A stand-in for the interface that resolves it on first use. See {@link IServiceContainer#memoize}.
     */
    @NotNull
    public static <T> T memoize(@NotNull Class<T> clazz) {
        return getRootContainer().memoize(clazz);
    }
}
