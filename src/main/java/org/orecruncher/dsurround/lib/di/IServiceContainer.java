package org.orecruncher.dsurround.lib.di;

import org.orecruncher.dsurround.lib.di.internal.DependencyContainer;

import java.util.function.Supplier;

@SuppressWarnings("unused")
public interface IServiceContainer {
    /**
     * Returns the name of the container
     */
    String get_name();

    /**
     * Registers the object in the container, with it being identified by its type.
     *
     * @param object Object instance to register
     * @param <T>    Type of object
     * @return Reference to the SimpleDIContainer for fluent declarations
     */
    @SuppressWarnings("unchecked")
    default <T> DependencyContainer registerSingleton(T object) {
        return this.registerSingleton((Class<T>) object.getClass(), object);
    }

    /**
     * Registers the class instance with the container, with it being identified by the
     * class type.  The object instantiation is deferred until the first time it is
     * requested.
     *
     * @param clazz        Type the object reference will be identified as
     * @param <T>          Type of object to represent the instance as
     * @return Reference to the SimpleDIContainer for fluent declarations
     */
    <T> DependencyContainer registerSingleton(Class<T> clazz);

    /**
     * Registers the object instance with the container, with it being identified by the
     * class type.
     *
     * @param clazz  Type the object reference will be identified as
     * @param object Object instance to register
     * @param <T>    Type of object to represent the instance as
     * @param <C>    Type of the object itself
     * @return Reference to the SimpleDIContainer fro fluent declarations
     */
    default <T, C extends T> DependencyContainer registerSingleton(Class<T> clazz, C object) {
        return this.registerFactory(clazz, () -> object);
    }

    /**
     * Registers the class instance with the container, with it being identified by the
     * class type.  The object instantiation is deferred until the first time it is
     * requested.
     *
     * @param clazz        Type the object reference will be identified as
     * @param desiredClass The class to instantiate that will represent the type
     * @param <T>          Type of object to represent the instance as
     * @return Reference to the SimpleDIContainer for fluent declarations
     */
    <T> DependencyContainer registerSingleton(Class<T> clazz, Class<? extends T> desiredClass);

    /**
     * Registers the factory supplier for the given type.  The Supplier will be invoked each time it is needed
     * for injection.  Supplied object references should be cached if possible.  A simple way to cache is to use
     * a Lazy<> supplier instance.
     *
     * @param clazz    Type the object reference will be identified as
     * @param supplier Supplier that provides the object instance as applicable
     * @param <T>      Type of object to represent the instance as
     * @return Reference to the SimpleDIContainer for fluent declarations
     */
    <T> DependencyContainer registerFactory(Class<T> clazz, Supplier<? extends T> supplier);

    /**
     * Resolves an instance of an object based on the provided class information.  If an object in the
     * container can meet the requirements it's instance would be provided.  Otherwise, an object is instantiated
     * and provided back to the caller.
     *
     * @param clazz Type the object reference will be identified as
     * @param <T>   Type of object to represent the instance as
     * @return Reference to the resolved object instance
     */
    <T> T resolve(Class<T> clazz);

    /**
     * Creates a child container with the specified name.
     *
     * @param name Name of the container to create
     * @return Newly created container
     */
    IServiceContainer createChildContainer(String name);
}
