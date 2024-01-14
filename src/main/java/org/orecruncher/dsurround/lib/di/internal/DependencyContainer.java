package org.orecruncher.dsurround.lib.di.internal;

import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.Singleton;
import org.orecruncher.dsurround.lib.di.*;
import org.orecruncher.dsurround.lib.Lazy;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Simple dependency injection container that injects via constructor as well as
 * Injection annotation.  Does not handle complex cases such as circular dependencies
 * so brain power may be required.
 */
@SuppressWarnings("unused")
public class DependencyContainer implements IServiceContainer {

    private final Map<Class<?>, Supplier<?>> _resolvers;
    private final String _name;
    private final ContainerManager _manager;
    @Nullable
    private final DependencyContainer _parent;

    public DependencyContainer(String containerName, ContainerManager manager) {
        this(containerName, manager, null);
    }

    public DependencyContainer(String containerName, ContainerManager manager, @Nullable DependencyContainer parent) {
        this._name = containerName;
        this._manager = manager;
        this._parent = parent;
        this._resolvers = new IdentityHashMap<>();
    }

    /**
     * Returns the name of the container
     */
    @Override
    public String getName() {
        return this._name;
    }

    /**
     * Dumps the registrations in the container
     */
    @Override
    public Stream<String> dumpRegistrations() {
        return this._resolvers.entrySet().stream()
                .map(kvp -> {
                    var builder = new StringBuilder();
                    builder.append(kvp.getKey().getName())
                            .append(" [");
                    if (kvp.getValue() instanceof Singleton)
                        builder.append("SINGLETON");
                    else
                        builder.append("PER INSTANCE");
                    return builder.append("]").toString();
                })
                .sorted();
    }

    /**
     * Creates a child container, using the current instance as the parent.  When resolving the parent is used
     * to resolve a class instance if no resolver is present in the current instance.
     *
     * @param containerName Name of the container to create
     * @return Container instance
     */
    @Override
    public IServiceContainer createChildContainer(String containerName) {
        var container = new DependencyContainer(containerName, this._manager, this);
        this._manager.registerContainer(container);
        return container;
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
    public <T> DependencyContainer registerSingleton(Class<T> clazz) {
        try {
            this.checkForKeySuitability(clazz);
            this.checkForResolverSuitability(clazz);
            return this.registerFactory(clazz, new Lazy<>(() -> this.createFactory(clazz).get()));
        } catch (Throwable ex) {
            Library.getLogger().error(ex, "Unable to register singleton %s", clazz.getName());
            throw ex;
        }
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
    @Override
    public <T> DependencyContainer registerSingleton(Class<T> clazz, Class<? extends T> desiredClass) {
        try {
            this.checkForKeySuitability(clazz);
            this.checkForResolverSuitability(desiredClass);
            return this.registerFactory(clazz, new Lazy<>(() -> this.createFactory(desiredClass).get()));
        } catch (Throwable ex) {
            Library.getLogger().error(ex, "Unable to register singleton %s using class %s", clazz.getName(), desiredClass.getName());
            throw ex;
        }
    }

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
    @Override
    public <T> DependencyContainer registerFactory(Class<T> clazz, Supplier<? extends T> supplier)
    {
        try {
            this.checkForKeySuitability(clazz);
            synchronized (this._resolvers) {
                this._resolvers.put(clazz, supplier);
            }
            return this;
        } catch (Throwable ex) {
            Library.getLogger().error(ex, "Unable to register factor for class %s", clazz.getName());
            throw ex;
        }
    }

    /**
     * Resolves an instance of an object based on the provided class information.  If an object in the
     * container can meet the requirements it's instance would be provided.  Otherwise, an object is instantiated
     * and provided back to the caller.
     *
     * @param clazz Type the object reference will be identified as
     * @param <T>   Type of object to represent the instance as
     * @return Reference to the resolved object instance
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> T resolve(Class<T> clazz) {
        try {
            var resolver = this.findResolver(clazz);
            if (resolver != null)
                return (T) resolver.get();

            // Have not seen.  Create a factory object for the class.
            var factory = this.createFactory(clazz);

            // Get the factory result
            var result = factory.get();

            // If the class is not annotated with cacheable, register the factory so subsequent
            // resolves will be pre-staged.  Otherwise, register the new class instance
            // as it will be a singleton.
            if (!clazz.isAnnotationPresent(Cacheable.class)) {
                this.registerFactory(clazz, factory);
            } else {
                this.registerFactory(clazz, new Singleton<>(result));
            }

            return result;
        } catch (Throwable ex) {
            Library.getLogger().error(ex, "Unable to resolve class %s", clazz.getName());
            throw ex;
        }
    }

    /**
     * Crawls through the class hierarchy looking for @Injection points.
     */
    private static List<Field> getInjectedFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();

        do {
            for (var field : clazz.getDeclaredFields())
                if (field.isAnnotationPresent(Injection.class))
                    fields.add(field);
            clazz = clazz.getSuperclass();
        } while (!clazz.equals(Object.class));

        return fields;
    }

    /**
     * Creates an object instance using the supplied constructor.
     */
    private static Object createInstance(Constructor<?> constructor) throws InvocationTargetException, InstantiationException, IllegalAccessException {
        return constructor.newInstance();
    }

    /**
     * Creates an object instance using the supplied constructor.  The parameter
     * resolvers are used to resolve the parameters that are to be passed in during
     * construction.
     */
    private static Object createInstance(Constructor<?> constructor, Supplier<?>[] parameterResolvers) throws InvocationTargetException, InstantiationException, IllegalAccessException {
        var parameters = new Object[parameterResolvers.length];
        for (int i = 0; i < parameters.length; i++)
            parameters[i] = parameterResolvers[i].get();
        return constructor.newInstance(parameters);
    }

    /**
     * Applies injectors to the provided object instance.
     */
    private static Object applyInjectors(Object instance, List<Consumer<Object>> injections) {
        for (var injector : injections)
            injector.accept(instance);
        return instance;
    }

    /**
     * Checks the class for suitability for use in dependency injection.  If not suitable a RuntimeException
     * is generated.
     *
     * @param clazz Class to check for suitability
     */
    private void checkForKeySuitability(Class<?> clazz) {
        if (clazz.isPrimitive())
            throw new RuntimeException(String.format("'%s' is a primitive type and cannot be used for dependency injection", clazz.getName()));
        if (clazz.equals(Object.class))
            throw new RuntimeException("Object is not a suitable key");
        if (clazz.equals(Optional.class))
            throw new RuntimeException("Cannot use an optional as a key");
        if (!Modifier.isPublic(clazz.getModifiers()))
            throw new RuntimeException(String.format("Class '%s' is not public", clazz.getName()));
    }

    /**
     * Checks the class for suitability for use in dependency injection.
     * If not suitable, a RuntimeException is generated.
     *
     * @param clazz Class to check for suitability
     */
    private void checkForResolverSuitability(Class<?> clazz) {
        if (clazz.isInterface())
            throw new RuntimeException(String.format("The class %s is an interface and cannot be instantiated directly", clazz.getName()));
        if (clazz.equals(Optional.class))
            throw new RuntimeException("Cannot use an optional as a resolved type");
        if (!Modifier.isPublic(clazz.getModifiers()))
            throw new RuntimeException(String.format("Class '%s' is not public", clazz.getName()));
    }

    /**
     * Recursively searches for a resolver to satisfy the request.
     * The Current container is searched prior to searching the parent as this allows
     * for override in a child container.
     *
     * @param clazz Instance resolver to locate
     * @return Resolver if found
     */
    @Nullable
    private Supplier<?> findResolver(Class<?> clazz) {
        Supplier<?> resolver;
        synchronized (this._resolvers) {
            resolver = this._resolvers.get(clazz);
            if (resolver == null && this._parent != null)
                resolver = this._parent.findResolver(clazz);
        }
        return resolver;
    }

    /**
     * Creates an instance of a class, resolving dependencies using information in the container chain.  Note that
     * the target class must have a single constructor otherwise it creates ambiguity and an exception is thrown.
     *
     * @param clazz Type to instantiate
     * @param <T>   Type of instance to create
     * @return Instance of the specified class
     */
    @SuppressWarnings("unchecked")
    private <T> Supplier<T> createFactory(Class<T> clazz) {
        this.checkForResolverSuitability(clazz);
        var constructor = this.findSuitableConstructor(clazz);
        var pTypes = constructor.getParameterTypes();
        var parameterResolvers = new Supplier<?>[pTypes.length];

        if (pTypes.length > 0)
            for (int i = 0; i < pTypes.length; i++) {
                var resolver = this.findResolver(pTypes[i]);
                if (resolver == null)
                    throw new RuntimeException(String.format("Unable to resolve type %s", pTypes[i].getName()));
                parameterResolvers[i] = resolver;
            }

        var injections = new ArrayList<Consumer<Object>>();

        // Scan through looking for fields that are tagged with @Injection to set them up
        for (var field : getInjectedFields(clazz)) {
            // Resolve the type
            var type = field.getType();
            var resolver = this.findResolver(type);
            if (resolver == null)
                throw new RuntimeException(String.format("Unable to resolve type %s", type.getName()));
            field.setAccessible(true);
            injections.add(obj -> {
                try {
                    field.set(obj, resolver.get());
                } catch (Throwable t) {
                    throw new RuntimeException(t.getMessage());
                }
            });
        }

        // Simple case of no parameters or injections
        if (pTypes.length == 0 && injections.isEmpty())
            return () -> {
                try {
                    return (T) createInstance(constructor);
                } catch (Throwable t) {
                    throw new RuntimeException(t);
                }
            };

        // Has parameters but no injections
        if (injections.isEmpty())
            return () -> {
                try {
                    return (T) createInstance(constructor, parameterResolvers);
                } catch (Throwable t) {
                    throw new RuntimeException(t);
                }
            };

        // No parameters but has injections
        if (pTypes.length == 0)
            return () -> {
                Object created = null;
                try {
                    created = createInstance(constructor);
                } catch (Throwable t) {
                    throw new RuntimeException(t);
                }
                return (T) applyInjectors(created, injections);
            };

        // Has both parameters and injections
        return () -> {
            Object created = null;
            try {
                created = createInstance(constructor, parameterResolvers);
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
            return (T) applyInjectors(created, injections);
        };
    }

    /**
     * Finds a suitable constructor to use when instantiating a class.  If a class has a single declared
     * constructor, it will be used.  As a fallback if a constructor has the DependencyConstructor annotation,
     * it will be selected.  If a class has more than one constructor, or if more than one constructor has
     * the annotation and exception will be generated.
     *
     * @param clazz Class to examine for a candidate constructor
     * @return Suitable constructor for creation
     */
    private Constructor<?> findSuitableConstructor(Class<?> clazz) {
        var constructors = clazz.getConstructors();

        if (constructors.length == 0)
            throw new RuntimeException(String.format("Class '%s' does not have any constructors declared", clazz.getName()));

        // Degenerative case
        if (constructors.length == 1) {
            return constructors[0];
        }

        //  More than one - search for a constructor marked with an appropriate annotation
        constructors = Arrays.stream(constructors)
                .filter(c -> c.isAnnotationPresent(DependencyConstructor.class))
                .toArray(size -> new Constructor<?>[size]);

        if (constructors.length == 0)
            throw new RuntimeException(String.format("Class '%s' has more than one constructor declared and none have the @DependencyConstructor annotation", clazz.getName()));
        else if (constructors.length > 1)
            throw new RuntimeException(String.format("Class '%s' has more than one constructor with the @DependencyConstructor annotation.  Only annotate a single constructor.", clazz.getName()));

        return constructors[0];
    }
}
