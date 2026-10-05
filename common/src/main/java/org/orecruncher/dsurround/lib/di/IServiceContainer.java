package org.orecruncher.dsurround.lib.di;

import org.orecruncher.dsurround.lib.function.SingletonSupplier;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * A simple dependency injection container. Types are registered as singletons or factories, and resolving a type
 * gives its instance. A concrete class that isn't registered is created on request, its constructor parameters
 * resolved the same way; it is cached if annotated {@link Cacheable}.
 * <p>
 * Registration methods return the container, so registrations can be chained. Problems are reported with a
 * {@link DependencyException} saying what failed.
 */
public interface IServiceContainer {
    /**
     * Returns the name of the container
     */
    String getName();

    /**
     * Describes each registration, for diagnostics.
     */
    Stream<String> dumpRegistrations();

    /**
     * Registers an existing object as a singleton, identified by its own class.
     */
    @SuppressWarnings("unchecked")
    default <T> IServiceContainer registerSingleton(T object) {
        return this.registerSingleton((Class<T>) object.getClass(), object);
    }

    /**
     * Registers a class as a singleton, identified by itself. It is created the first time it is requested.
     */
    <T> IServiceContainer registerSingleton(Class<T> clazz);

    /**
     * Registers an existing object as the singleton for {@code clazz}.
     */
    default <T, C extends T> IServiceContainer registerSingleton(Class<T> clazz, C object) {
        return this.registerFactory(clazz, SingletonSupplier.of(object));
    }

    /**
     * Registers {@code desiredClass} as the singleton for {@code clazz} (typically an interface and its
     * implementation). It is created the first time it is requested.
     */
    <T> IServiceContainer registerSingleton(Class<T> clazz, Class<? extends T> desiredClass);

    /**
     * Registers a supplier for {@code clazz}. It is called every time the type is resolved, so it should cache its
     * result if a single instance is wanted. Replaces any earlier registration for the type.
     */
    <T> IServiceContainer registerFactory(Class<T> clazz, Supplier<? extends T> supplier);

    /**
     * Returns the instance for {@code clazz}: from its registration if it has one, otherwise by creating the class
     * (see the class description).
     *
     * @throws DependencyException if it can't be resolved or created
     */
    <T> T resolve(Class<T> clazz);

    /**
     * Returns a stand-in for the interface {@code clazz} that resolves the real instance the first time one of its
     * methods is called. Useful for static fields initialized before the container is set up.
     * <p>
     * Every call goes through reflection, so avoid it on hot paths; a holder class or a Supplier is cheaper.
     *
     * @throws IllegalArgumentException if {@code clazz} is not an interface
     */
    <T> T memoize(Class<T> clazz);

    /**
     * Checks the registrations without creating anything, following what each registered class (and each of
     * {@code additionalRoots}) would need to be created. Reports circular dependencies and dependencies that can't
     * be satisfied. What a registered object or supplier depends on can't be seen, so those aren't followed.
     * <p>
     * Call it once registration is finished: before then, a missing dependency may just not be registered yet.
     *
     * @param additionalRoots classes that will be resolved without being registered
     * @return a description of each problem found, empty if there are none
     */
    List<String> validate(Class<?>... additionalRoots);
}
