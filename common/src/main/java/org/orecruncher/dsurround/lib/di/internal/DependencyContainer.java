package org.orecruncher.dsurround.lib.di.internal;

import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.lib.SingletonSupplier;
import org.orecruncher.dsurround.lib.di.*;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Dependency injection container that creates objects through their constructor, resolving its parameters from
 * the container, and then sets any fields marked {@link Injection}.
 * <p>
 * Circular dependencies are not supported. They are reported, with the path, as a {@link DependencyException} when
 * creation runs into one, and {@link #validate} can find them (and missing dependencies) before anything is created.
 * <p>
 * Thread safety: lookups are lock-free. Everything that creates an object (a lazy singleton's first use, or
 * creating and registering a class that wasn't registered) happens under one lock, so a singleton is only ever
 * created once, and creations on different threads can't deadlock against each other. The lock is reentrant, so
 * creating an object can resolve and create its dependencies.
 */
public final class DependencyContainer implements IServiceContainer {

    private final Map<Class<?>, Supplier<?>> resolvers = new ConcurrentHashMap<>();
    // For registrations backed by a class the container creates: the class it creates. Registrations of an
    // existing object or a supplier have no entry (the container can't see what they depend on). Used by validate().
    private final Map<Class<?>, Class<?>> implementations = new ConcurrentHashMap<>();
    // The classes this thread is in the middle of creating, outermost first, to catch circular dependencies. Per
    // thread rather than under the creation lock, because per-instance factories run outside that lock.
    private final ThreadLocal<ArrayDeque<Class<?>>> creating = ThreadLocal.withInitial(ArrayDeque::new);
    private final Object creationLock = new Object();
    private final String name;

    public DependencyContainer(String containerName) {
        this.name = containerName;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public Stream<String> dumpRegistrations() {
        return this.resolvers.entrySet().stream()
                .map(kvp -> kvp.getKey().getName() + " [" + (isSingleton(kvp.getValue()) ? "SINGLETON" : "PER INSTANCE") + "]")
                .sorted();
    }

    private static boolean isSingleton(Supplier<?> supplier) {
        return supplier instanceof SingletonSupplier || supplier instanceof LazySingleton;
    }

    // ---- Registration ----------------------------------------------------------------------------------------

    @Override
    public <T> IServiceContainer registerSingleton(Class<T> clazz) {
        return this.registerSingleton(clazz, clazz);
    }

    @Override
    public <T> IServiceContainer registerSingleton(Class<T> clazz, Class<? extends T> desiredClass) {
        checkKeySuitability(clazz);
        checkCreatable(desiredClass);
        this.register(clazz, new LazySingleton<>(() -> this.createFactory(desiredClass).get()), desiredClass);
        return this;
    }

    @Override
    public <T> IServiceContainer registerFactory(Class<T> clazz, Supplier<? extends T> supplier) {
        checkKeySuitability(clazz);
        Objects.requireNonNull(supplier, "supplier");
        this.register(clazz, supplier, null);
        return this;
    }

    /**
     * Records a registration, replacing any earlier one for the type.
     *
     * @param implementation the class the container creates for it, or null if it is an existing object or a
     *                       supplier
     */
    private void register(Class<?> clazz, Supplier<?> supplier, @Nullable Class<?> implementation) {
        if (implementation != null)
            this.implementations.put(clazz, implementation);
        else
            this.implementations.remove(clazz);
        this.resolvers.put(clazz, supplier);
    }

    // ---- Resolution ------------------------------------------------------------------------------------------

    @Override
    @SuppressWarnings("unchecked")
    public <T> T resolve(Class<T> clazz) {
        var resolver = this.resolvers.get(clazz);
        if (resolver != null)
            return (T) resolver.get();

        // Not registered: create it, and register how to get it again. Done under the creation lock so two threads
        // asking for the same class at once don't each create (and register) their own.
        synchronized (this.creationLock) {
            resolver = this.resolvers.get(clazz);
            if (resolver != null)
                return (T) resolver.get();

            var factory = this.createFactory(clazz);
            if (clazz.isAnnotationPresent(Cacheable.class)) {
                // A single instance for the life of the container
                var instance = factory.get();
                this.register(clazz, SingletonSupplier.of(instance), clazz);
                return instance;
            }

            // A new instance each time
            this.register(clazz, factory, clazz);
            return factory.get();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T memoize(Class<T> clazz) {
        if (!clazz.isInterface())
            throw new IllegalArgumentException(String.format("memoize() needs an interface; '%s' is a class", clazz.getName()));

        // A LazySingleton, so the first resolve happens under the same single creation lock as everything else.
        // (A constructor running under that lock may call a memoized logger; a separate lock here could deadlock.)
        Supplier<T> target = new LazySingleton<>(() -> this.resolve(clazz));
        InvocationHandler handler = (proxy, method, args) -> {
            try {
                return method.invoke(target.get(), args);
            } catch (InvocationTargetException e) {
                // Rethrow what the target threw. Left wrapped, the proxy would turn it into an
                // UndeclaredThrowableException, hiding the real exception from the caller.
                throw e.getCause();
            }
        };
        return (T) Proxy.newProxyInstance(clazz.getClassLoader(), new Class<?>[]{clazz}, handler);
    }

    // ---- Creation --------------------------------------------------------------------------------------------

    /**
     * A singleton created on first use. Creation happens under the container's single creation lock rather than a
     * lock of its own: with a lock per singleton, two threads creating different objects could take the locks in
     * opposite orders and deadlock.
     */
    private final class LazySingleton<T> implements Supplier<T> {
        private final Supplier<T> factory;
        private volatile T instance;

        LazySingleton(Supplier<T> factory) {
            this.factory = factory;
        }

        @Override
        public T get() {
            var result = this.instance;
            if (result != null)
                return result;
            synchronized (DependencyContainer.this.creationLock) {
                if (this.instance == null)
                    this.instance = this.factory.get();
                return this.instance;
            }
        }
    }

    /**
     * Builds a supplier that creates instances of {@code clazz}: it picks the constructor, then on each call
     * resolves the constructor's parameters, creates the instance and sets its {@link Injection} fields.
     * <p>
     * Parameters and fields are resolved when the instance is created, with {@link #resolve}, so a dependency
     * that isn't registered is created the same way a top-level request would be, whatever was resolved before.
     */
    @SuppressWarnings("unchecked")
    private <T> Supplier<T> createFactory(Class<T> clazz) {
        checkCreatable(clazz);
        var constructor = findSuitableConstructor(clazz);
        var parameterTypes = constructor.getParameterTypes();
        var injectedFields = getInjectedFields(clazz);
        for (var field : injectedFields)
            field.setAccessible(true);

        return () -> {
            // If this thread is already creating this class further up, its dependencies lead back to it. Without
            // this check that recursion would run until the stack overflowed.
            var stack = this.creating.get();
            if (stack.contains(clazz))
                throw new DependencyException("Circular dependency: " + formatCycle(stack, clazz));
            stack.addLast(clazz);
            try {
                return this.createInstance(clazz, constructor, parameterTypes, injectedFields);
            } finally {
                stack.removeLast();
            }
        };
    }

    @SuppressWarnings("unchecked")
    private <T> T createInstance(Class<T> clazz, Constructor<?> constructor, Class<?>[] parameterTypes, List<Field> injectedFields) {
        var parameters = new Object[parameterTypes.length];
        for (int i = 0; i < parameters.length; i++)
            parameters[i] = this.resolveDependency(parameterTypes[i], clazz, "constructor parameter");

        T instance;
        try {
            instance = (T) constructor.newInstance(parameters);
        } catch (InvocationTargetException e) {
            // The constructor threw: report that exception itself, not the reflection wrapper
            var cause = e.getCause();
            if (cause instanceof VirtualMachineError fatal)
                throw fatal;
            throw new DependencyException(String.format("Constructor of '%s' threw %s", clazz.getName(), cause), cause);
        } catch (ReflectiveOperationException e) {
            throw new DependencyException(String.format("Unable to create '%s': %s", clazz.getName(), e), e);
        }

        for (var field : injectedFields) {
            var value = this.resolveDependency(field.getType(), clazz, "field '" + field.getName() + "'");
            try {
                field.set(instance, value);
            } catch (IllegalAccessException e) {
                throw new DependencyException(String.format("Unable to set field '%s' of '%s'", field.getName(), clazz.getName()), e);
            }
        }
        return instance;
    }

    /**
     * The part of {@code path} from {@code repeated}'s first appearance, then {@code repeated} again, as in
     * "A -> B -> C -> A".
     */
    private static String formatCycle(Collection<Class<?>> path, Class<?> repeated) {
        var names = new ArrayList<String>();
        boolean inCycle = false;
        for (var c : path) {
            if (c == repeated)
                inCycle = true;
            if (inCycle)
                names.add(c.getSimpleName());
        }
        names.add(repeated.getSimpleName());
        return String.join(" -> ", names);
    }

    // ---- Validation ------------------------------------------------------------------------------------------

    /**
     * Checks the registrations without creating anything. Starting from every registration backed by a class (and
     * from {@code additionalRoots}, for classes that will be resolved without being registered), it follows
     * constructor parameter and {@link Injection} field types: through registrations to the classes behind them,
     * and through unregistered classes, which would be created on request.
     * <p>
     * Reports circular dependencies (with the path), dependencies that can't be satisfied (an interface or
     * abstract class with no registration, a class that isn't public), and classes the container couldn't pick a
     * constructor for. Registrations of an existing object or a supplier are dead ends: what they depend on can't
     * be seen.
     *
     * @return the problems found, empty if there are none
     */
    @Override
    public List<String> validate(Class<?>... additionalRoots) {
        var problems = new ArrayList<String>();
        var visit = new Validation(problems);

        // Sorted so the report is the same from run to run
        var roots = new TreeSet<Class<?>>(Comparator.comparing(Class::getName));
        roots.addAll(this.implementations.keySet());
        roots.addAll(Arrays.asList(additionalRoots));
        for (var root : roots)
            visit.visit(root, null);

        return problems;
    }

    /**
     * Depth-first walk of the dependency graph for {@link #validate}.
     */
    private final class Validation {
        private final List<String> problems;
        // Types fully checked
        private final Set<Class<?>> done = new HashSet<>();
        // The path from the root being checked to the current type
        private final ArrayDeque<Class<?>> path = new ArrayDeque<>();
        // Each cycle is reported once, however many roots lead into it
        private final Set<Set<Class<?>>> reportedCycles = new HashSet<>();

        Validation(List<String> problems) {
            this.problems = problems;
        }

        void visit(Class<?> type, @Nullable Class<?> neededBy) {
            if (this.done.contains(type))
                return;
            if (this.path.contains(type)) {
                var cycleMembers = new HashSet<Class<?>>();
                boolean inCycle = false;
                for (var c : this.path) {
                    if (c == type)
                        inCycle = true;
                    if (inCycle)
                        cycleMembers.add(c);
                }
                if (this.reportedCycles.add(cycleMembers))
                    this.problems.add("Circular dependency: " + formatCycle(this.path, type));
                return;
            }

            this.path.addLast(type);
            try {
                var implementation = this.implementationFor(type, neededBy);
                if (implementation != null) {
                    for (var dependency : this.dependenciesOf(implementation))
                        this.visit(dependency, type);
                }
            } finally {
                this.path.removeLast();
                this.done.add(type);
            }
        }

        /**
         * The class the container would create for {@code type}, or null if there is none to follow (an existing
         * object, a supplier, or a type that can't be created, which is reported).
         */
        @Nullable
        private Class<?> implementationFor(Class<?> type, @Nullable Class<?> neededBy) {
            if (DependencyContainer.this.resolvers.containsKey(type))
                return DependencyContainer.this.implementations.get(type);
            try {
                checkKeySuitability(type);
                checkCreatable(type);
                return type;
            } catch (DependencyException e) {
                this.problems.add(neededBy != null
                        ? String.format("'%s' needs '%s': %s", neededBy.getName(), type.getName(), e.getMessage())
                        : e.getMessage());
                return null;
            }
        }

        /**
         * Constructor parameter and injected field types of {@code implementation}.
         */
        private List<Class<?>> dependenciesOf(Class<?> implementation) {
            var dependencies = new ArrayList<Class<?>>();
            try {
                dependencies.addAll(Arrays.asList(findSuitableConstructor(implementation).getParameterTypes()));
            } catch (DependencyException e) {
                this.problems.add(e.getMessage());
            }
            for (var field : getInjectedFields(implementation))
                dependencies.add(field.getType());
            return dependencies;
        }
    }

    /**
     * Resolves a dependency of {@code owner}, adding which class needed it to any failure.
     */
    private Object resolveDependency(Class<?> type, Class<?> owner, String usage) {
        try {
            return this.resolve(type);
        } catch (DependencyException e) {
            throw new DependencyException(String.format("Unable to resolve %s '%s' of '%s': %s", usage, type.getName(), owner.getName(), e.getMessage()), e);
        }
    }

    /**
     * The fields marked {@link Injection} in the class and its superclasses.
     */
    private static List<Field> getInjectedFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            for (var field : c.getDeclaredFields())
                if (field.isAnnotationPresent(Injection.class))
                    fields.add(field);
        }
        return fields;
    }

    /**
     * The constructor to create the class with: its only public constructor, or else the one marked
     * {@link DependencyConstructor}.
     */
    private static Constructor<?> findSuitableConstructor(Class<?> clazz) {
        var constructors = clazz.getConstructors();

        if (constructors.length == 0)
            throw new DependencyException(String.format("Class '%s' has no public constructor", clazz.getName()));
        if (constructors.length == 1)
            return constructors[0];

        var marked = Arrays.stream(constructors)
                .filter(c -> c.isAnnotationPresent(DependencyConstructor.class))
                .toArray(Constructor<?>[]::new);

        if (marked.length == 0)
            throw new DependencyException(String.format("Class '%s' has more than one constructor and none has the @DependencyConstructor annotation", clazz.getName()));
        if (marked.length > 1)
            throw new DependencyException(String.format("Class '%s' has more than one constructor with the @DependencyConstructor annotation; only annotate one", clazz.getName()));
        return marked[0];
    }

    // ---- Checks ----------------------------------------------------------------------------------------------

    /**
     * Checks that a type can identify a registration.
     */
    private static void checkKeySuitability(Class<?> clazz) {
        if (clazz.isPrimitive())
            throw new DependencyException(String.format("'%s' is a primitive type and cannot be used for dependency injection", clazz.getName()));
        if (clazz.equals(Object.class))
            throw new DependencyException("Object is not a suitable key");
        if (clazz.equals(Optional.class))
            throw new DependencyException("Cannot use an Optional as a key");
        if (!Modifier.isPublic(clazz.getModifiers()))
            throw new DependencyException(String.format("Class '%s' is not public", clazz.getName()));
    }

    /**
     * Checks that the container can create instances of a class.
     */
    private static void checkCreatable(Class<?> clazz) {
        if (clazz.isInterface())
            throw new DependencyException(String.format("'%s' is an interface with no registration, so it cannot be created", clazz.getName()));
        if (Modifier.isAbstract(clazz.getModifiers()))
            throw new DependencyException(String.format("'%s' is abstract with no registration, so it cannot be created", clazz.getName()));
        if (clazz.equals(Optional.class))
            throw new DependencyException("Cannot use an Optional as a resolved type");
        if (!Modifier.isPublic(clazz.getModifiers()))
            throw new DependencyException(String.format("Class '%s' is not public", clazz.getName()));
    }
}
