package org.orecruncher.dsurround.lib.di;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.lib.di.internal.DependencyContainer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the dependency injection container. Plain Java: no Minecraft needed.
 * <p>
 * The container only works with public classes, so the fixtures are public nested classes.
 */
public class DependencyContainerTests {

    private IServiceContainer container;

    @BeforeEach
    void setUp() {
        this.container = new DependencyContainer("test");
        Simple.CREATED.set(0);
        CachedThing.CREATED.set(0);
        ServiceImpl.CREATED.set(0);
        SlowCached.CREATED.set(0);
        LazyDependency.CREATED.set(0);
    }

    // ---- Fixtures --------------------------------------------------------------------------------------------

    public static class Simple {
        public static final AtomicInteger CREATED = new AtomicInteger();

        public Simple() {
            CREATED.incrementAndGet();
        }
    }

    @Cacheable
    public static class CachedThing {
        public static final AtomicInteger CREATED = new AtomicInteger();

        public CachedThing() {
            CREATED.incrementAndGet();
        }
    }

    public interface Service {
        String name();
    }

    public static class ServiceImpl implements Service {
        public static final AtomicInteger CREATED = new AtomicInteger();

        public ServiceImpl() {
            CREATED.incrementAndGet();
        }

        @Override
        public String name() {
            return "impl";
        }
    }

    public static class NeedsSimple {
        public final Simple simple;

        public NeedsSimple(Simple simple) {
            this.simple = simple;
        }
    }

    public static class Inner {
    }

    public static class Outer {
        public final Inner inner;

        public Outer(Inner inner) {
            this.inner = inner;
        }
    }

    public static class NeedsService {
        public NeedsService(Service service) {
        }
    }

    public static class TwoConstructors {
        public TwoConstructors() {
        }

        public TwoConstructors(Simple simple) {
        }
    }

    public static class MarkedConstructor {
        public boolean usedMarked;

        public MarkedConstructor() {
        }

        @DependencyConstructor
        public MarkedConstructor(Simple simple) {
            this.usedMarked = simple != null;
        }
    }

    public static class TwoMarked {
        @DependencyConstructor
        public TwoMarked() {
        }

        @DependencyConstructor
        public TwoMarked(Simple simple) {
        }
    }

    public static class Throws {
        public Throws() {
            throw new IllegalStateException("boom");
        }
    }

    public static class WithFields {
        @Injection
        public Simple publicField;
        @Injection
        private Simple privateField;
        public final boolean injectedBeforeConstructorEnded;

        public WithFields() {
            this.injectedBeforeConstructorEnded = this.publicField != null;
        }

        public Simple privateField() {
            return this.privateField;
        }
    }

    public interface Thrower {
        void go();
    }

    public static class ThrowerImpl implements Thrower {
        @Override
        public void go() {
            throw new IllegalStateException("from target");
        }
    }

    @Cacheable
    public static class SlowCached {
        public static final AtomicInteger CREATED = new AtomicInteger();

        public SlowCached() throws InterruptedException {
            CREATED.incrementAndGet();
            Thread.sleep(50);
        }
    }

    public static class SlowUnregistered {
        public SlowUnregistered() throws InterruptedException {
            Thread.sleep(50);
        }
    }

    public static class LazyDependency {
        public static final AtomicInteger CREATED = new AtomicInteger();

        public LazyDependency(SlowUnregistered dependency) {
            CREATED.incrementAndGet();
        }
    }

    public static class NeedsLazyDependency {
        public NeedsLazyDependency(SlowUnregistered first, LazyDependency second) {
        }
    }

    public static abstract class AbstractThing {
    }

    static class NotPublic {
    }

    // ---- Registration ----------------------------------------------------------------------------------------

    @Test
    void registeredSingletonIsCreatedOnceOnFirstUse() {
        this.container.registerSingleton(Simple.class);
        assertEquals(0, Simple.CREATED.get(), "not created at registration");

        var first = this.container.resolve(Simple.class);
        var second = this.container.resolve(Simple.class);

        assertSame(first, second);
        assertEquals(1, Simple.CREATED.get());
    }

    @Test
    void interfaceRegisteredWithImplementation() {
        this.container.registerSingleton(Service.class, ServiceImpl.class);

        var service = this.container.resolve(Service.class);

        assertInstanceOf(ServiceImpl.class, service);
        assertSame(service, this.container.resolve(Service.class));
    }

    @Test
    void existingObjectRegisteredAsSingleton() {
        var impl = new ServiceImpl();
        this.container.registerSingleton(Service.class, impl);

        assertSame(impl, this.container.resolve(Service.class));
    }

    @Test
    void factoryIsCalledForEveryResolve() {
        var calls = new AtomicInteger();
        this.container.registerFactory(Simple.class, () -> {
            calls.incrementAndGet();
            return new Simple();
        });

        assertNotSame(this.container.resolve(Simple.class), this.container.resolve(Simple.class));
        assertEquals(2, calls.get());
    }

    @Test
    void laterRegistrationReplacesEarlier() {
        var a = new ServiceImpl();
        var b = new ServiceImpl();
        this.container.registerSingleton(Service.class, a);
        this.container.registerSingleton(Service.class, b);

        assertSame(b, this.container.resolve(Service.class));
    }

    @Test
    void registrationsCanBeChained() {
        this.container
                .registerSingleton(Simple.class)
                .registerSingleton(Service.class, ServiceImpl.class);

        assertNotNull(this.container.resolve(Simple.class));
        assertNotNull(this.container.resolve(Service.class));
    }

    @Test
    void unsuitableKeysAreRejected() {
        assertThrows(DependencyException.class, () -> this.container.registerFactory(Object.class, Object::new));
        assertThrows(DependencyException.class, () -> this.container.registerFactory(int.class, () -> 1));
        var e = assertThrows(DependencyException.class, () -> this.container.registerSingleton(NotPublic.class));
        assertTrue(e.getMessage().contains("not public"), e.getMessage());
    }

    @Test
    void dumpDescribesRegistrations() {
        this.container.registerSingleton(Simple.class);
        this.container.registerFactory(Service.class, ServiceImpl::new);

        var lines = this.container.dumpRegistrations().toList();

        assertTrue(lines.contains(Simple.class.getName() + " [SINGLETON]"), lines.toString());
        assertTrue(lines.contains(Service.class.getName() + " [PER INSTANCE]"), lines.toString());
    }

    // ---- Resolving unregistered classes ----------------------------------------------------------------------

    @Test
    void unregisteredClassIsCreatedEachTime() {
        assertNotSame(this.container.resolve(Simple.class), this.container.resolve(Simple.class));
        assertEquals(2, Simple.CREATED.get());
    }

    @Test
    void unregisteredCacheableClassIsCreatedOnce() {
        assertSame(this.container.resolve(CachedThing.class), this.container.resolve(CachedThing.class));
        assertEquals(1, CachedThing.CREATED.get());
    }

    @Test
    void constructorParametersAreResolved() {
        this.container.registerSingleton(Simple.class);

        var needs = this.container.resolve(NeedsSimple.class);

        assertSame(this.container.resolve(Simple.class), needs.simple);
    }

    @Test
    void unregisteredConstructorParameterIsCreated() {
        // Regression: a constructor parameter was only looked up among registrations, so this failed unless
        // something had happened to resolve Inner first
        var outer = this.container.resolve(Outer.class);

        assertNotNull(outer.inner);
    }

    @Test
    void cannotCreateAnUnregisteredInterface() {
        var e = assertThrows(DependencyException.class, () -> this.container.resolve(Service.class));
        assertTrue(e.getMessage().contains("interface"), e.getMessage());
    }

    @Test
    void cannotCreateAnAbstractClass() {
        var e = assertThrows(DependencyException.class, () -> this.container.resolve(AbstractThing.class));
        assertTrue(e.getMessage().contains("abstract"), e.getMessage());
    }

    @Test
    void failureNamesWhatNeededTheMissingDependency() {
        var e = assertThrows(DependencyException.class, () -> this.container.resolve(NeedsService.class));

        assertTrue(e.getMessage().contains(Service.class.getName()), e.getMessage());
        assertTrue(e.getMessage().contains(NeedsService.class.getName()), e.getMessage());
        assertTrue(e.getMessage().contains("constructor parameter"), e.getMessage());
    }

    // ---- Constructors ----------------------------------------------------------------------------------------

    @Test
    void severalConstructorsWithoutAnnotationIsAnError() {
        var e = assertThrows(DependencyException.class, () -> this.container.resolve(TwoConstructors.class));
        assertTrue(e.getMessage().contains("@DependencyConstructor"), e.getMessage());
    }

    @Test
    void markedConstructorIsUsed() {
        assertTrue(this.container.resolve(MarkedConstructor.class).usedMarked);
    }

    @Test
    void severalMarkedConstructorsIsAnError() {
        var e = assertThrows(DependencyException.class, () -> this.container.resolve(TwoMarked.class));
        assertTrue(e.getMessage().contains("only annotate one"), e.getMessage());
    }

    @Test
    void constructorExceptionIsTheCause() {
        // Regression: the constructor's exception was wrapped twice (RuntimeException > InvocationTargetException)
        var e = assertThrows(DependencyException.class, () -> this.container.resolve(Throws.class));

        assertInstanceOf(IllegalStateException.class, e.getCause());
        assertEquals("boom", e.getCause().getMessage());
        assertTrue(e.getMessage().contains(Throws.class.getName()), e.getMessage());
    }

    // ---- Field injection -------------------------------------------------------------------------------------

    @Test
    void injectedFieldsAreSetAfterConstruction() {
        var instance = this.container.resolve(WithFields.class);

        assertNotNull(instance.publicField);
        assertNotNull(instance.privateField());
        assertFalse(instance.injectedBeforeConstructorEnded, "fields are set after the constructor runs");
    }

    // ---- memoize ---------------------------------------------------------------------------------------------

    @Test
    void memoizeResolvesOnFirstUseOnly() {
        var resolves = new AtomicInteger();
        this.container.registerFactory(Service.class, () -> {
            resolves.incrementAndGet();
            return new ServiceImpl();
        });

        var service = this.container.memoize(Service.class);
        assertEquals(0, resolves.get(), "nothing resolved until used");

        assertEquals("impl", service.name());
        assertEquals("impl", service.name());
        assertEquals(1, resolves.get());
    }

    @Test
    void memoizeRethrowsTheTargetsException() {
        // Regression: the proxy turned it into UndeclaredThrowableException
        this.container.registerSingleton(Thrower.class, ThrowerImpl.class);
        var thrower = this.container.memoize(Thrower.class);

        var e = assertThrows(IllegalStateException.class, thrower::go);
        assertEquals("from target", e.getMessage());
    }

    @Test
    void memoizeNeedsAnInterface() {
        var e = assertThrows(IllegalArgumentException.class, () -> this.container.memoize(Simple.class));
        assertTrue(e.getMessage().contains("interface"), e.getMessage());
    }

    // ---- Threads ---------------------------------------------------------------------------------------------

    @Test
    void concurrentFirstResolveOfCacheableCreatesOneInstance() throws Exception {
        // Regression: find, create and register were separate steps, so racing threads could each create one
        int threads = 8;
        var pool = Executors.newFixedThreadPool(threads);
        var start = new CountDownLatch(1);
        try {
            List<Future<SlowCached>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++)
                results.add(pool.submit(() -> {
                    start.await();
                    return this.container.resolve(SlowCached.class);
                }));
            start.countDown();

            Set<SlowCached> distinct = Collections.newSetFromMap(new IdentityHashMap<>());
            for (var f : results)
                distinct.add(f.get(10, TimeUnit.SECONDS));

            assertEquals(1, distinct.size());
            assertEquals(1, SlowCached.CREATED.get());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void lazySingletonAndUnregisteredCreationDoNotDeadlock() {
        // One thread creates an unregistered class that needs a lazy singleton; the other creates that singleton,
        // which needs an unregistered class. With a lock per singleton plus a container lock, these could take the
        // locks in opposite orders. With one creation lock they just take turns.
        this.container.registerSingleton(LazyDependency.class);

        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            var pool = Executors.newFixedThreadPool(2);
            var start = new CountDownLatch(1);
            try {
                var a = pool.submit(() -> {
                    start.await();
                    return this.container.resolve(NeedsLazyDependency.class);
                });
                var b = pool.submit(() -> {
                    start.await();
                    return this.container.resolve(LazyDependency.class);
                });
                start.countDown();
                assertNotNull(a.get());
                assertNotNull(b.get());
            } finally {
                pool.shutdownNow();
            }
        });

        assertEquals(1, LazyDependency.CREATED.get());
    }

    // ---- Circular dependencies: fixtures ---------------------------------------------------------------------

    public static class CycleA {
        public CycleA(CycleB b) {
        }
    }

    public static class CycleB {
        public CycleB(CycleC c) {
        }
    }

    public static class CycleC {
        public CycleC(CycleA a) {
        }
    }

    public static class SelfCycle {
        public SelfCycle(SelfCycle self) {
        }
    }

    public static class SingletonCycleA {
        public static final AtomicInteger CREATED = new AtomicInteger();

        public SingletonCycleA(SingletonCycleB b) {
            CREATED.incrementAndGet();
        }
    }

    public static class SingletonCycleB {
        public static final AtomicInteger CREATED = new AtomicInteger();

        public SingletonCycleB(SingletonCycleA a) {
            CREATED.incrementAndGet();
        }
    }

    public static class FieldCycleA {
        @Injection
        public FieldCycleB b;
    }

    public static class FieldCycleB {
        public FieldCycleB(FieldCycleA a) {
        }
    }

    public static class ServiceUser implements Service {
        public ServiceUser(Service service) {
        }

        @Override
        public String name() {
            return "user";
        }
    }

    public static class SharedDependency {
        public final Simple first;
        public final Simple second;
        public final NeedsSimple third;

        public SharedDependency(Simple first, Simple second, NeedsSimple third) {
            this.first = first;
            this.second = second;
            this.third = third;
        }
    }

    public static class EntersCycle {
        public EntersCycle(CycleA a) {
        }
    }

    // ---- Circular dependencies: during creation --------------------------------------------------------------

    @Test
    void cycleOfUnregisteredClassesIsReportedWithItsPath() {
        // Regression: this recursed until StackOverflowError
        var e = assertThrows(DependencyException.class, () -> this.container.resolve(CycleA.class));

        assertTrue(e.getMessage().contains("Circular dependency: CycleA -> CycleB -> CycleC -> CycleA"), e.getMessage());
    }

    @Test
    void classNeedingItselfIsACycle() {
        var e = assertThrows(DependencyException.class, () -> this.container.resolve(SelfCycle.class));

        assertTrue(e.getMessage().contains("Circular dependency: SelfCycle -> SelfCycle"), e.getMessage());
    }

    @Test
    void cycleOfRegisteredSingletonsIsReported() {
        this.container
                .registerSingleton(SingletonCycleA.class)
                .registerSingleton(SingletonCycleB.class);

        var e = assertThrows(DependencyException.class, () -> this.container.resolve(SingletonCycleA.class));

        assertTrue(e.getMessage().contains("Circular dependency: SingletonCycleA -> SingletonCycleB -> SingletonCycleA"), e.getMessage());
    }

    @Test
    void cycleThroughInjectedFieldIsReported() {
        var e = assertThrows(DependencyException.class, () -> this.container.resolve(FieldCycleA.class));

        assertTrue(e.getMessage().contains("Circular dependency: FieldCycleA -> FieldCycleB -> FieldCycleA"), e.getMessage());
    }

    @Test
    void cycleThroughFactorySupplierIsReported() {
        // The supplier is opaque, but the classes it resolves still go through the check
        this.container.registerFactory(Service.class, () -> this.container.resolve(ServiceUser.class));

        var e = assertThrows(DependencyException.class, () -> this.container.resolve(Service.class));

        assertTrue(e.getMessage().contains("Circular dependency: ServiceUser -> ServiceUser"), e.getMessage());
    }

    @Test
    void sharedDependencyIsNotACycle() {
        // Simple is needed three times, once through NeedsSimple, but never while it is being created
        var shared = this.container.resolve(SharedDependency.class);

        assertNotNull(shared.first);
        assertNotNull(shared.second);
        assertNotNull(shared.third.simple);
    }

    @Test
    void containerStillWorksAfterACycleFailure() {
        assertThrows(DependencyException.class, () -> this.container.resolve(CycleA.class));

        // The creation path is unwound by the failure, so later creations aren't mistaken for cycles
        assertNotNull(this.container.resolve(Outer.class));
        assertNotNull(this.container.resolve(SharedDependency.class));
    }

    // ---- Circular dependencies: validate() -------------------------------------------------------------------

    @Test
    void validateFindsNothingWrongWithGoodRegistrations() {
        this.container
                .registerSingleton(Service.class, ServiceImpl.class)
                .registerSingleton(Outer.class)
                .registerSingleton(SharedDependency.class)
                .registerSingleton(WithFields.class);

        assertEquals(List.of(), this.container.validate());
    }

    @Test
    void validateReportsCycleWithoutCreatingAnything() {
        SingletonCycleA.CREATED.set(0);
        SingletonCycleB.CREATED.set(0);
        this.container
                .registerSingleton(SingletonCycleA.class)
                .registerSingleton(SingletonCycleB.class);

        var problems = this.container.validate();

        assertEquals(List.of("Circular dependency: SingletonCycleA -> SingletonCycleB -> SingletonCycleA"), problems);
        assertEquals(0, SingletonCycleA.CREATED.get());
        assertEquals(0, SingletonCycleB.CREATED.get());
    }

    @Test
    void validateReportsEachCycleOnce() {
        // Every class in the cycle, and one leading into it, is a root
        this.container
                .registerSingleton(EntersCycle.class)
                .registerSingleton(CycleA.class)
                .registerSingleton(CycleB.class)
                .registerSingleton(CycleC.class);

        var problems = this.container.validate();

        assertEquals(1, problems.size(), problems.toString());
        assertTrue(problems.getFirst().startsWith("Circular dependency: "), problems.toString());
    }

    @Test
    void validateFollowsUnregisteredClasses() {
        // Only EntersCycle is registered; the cycle is among classes that would be created on request
        this.container.registerSingleton(EntersCycle.class);

        assertEquals(List.of("Circular dependency: CycleA -> CycleB -> CycleC -> CycleA"), this.container.validate());
    }

    @Test
    void validateReportsMissingDependency() {
        this.container.registerSingleton(NeedsService.class);

        var problems = this.container.validate();

        assertEquals(1, problems.size(), problems.toString());
        assertTrue(problems.getFirst().contains("'" + NeedsService.class.getName() + "' needs '" + Service.class.getName() + "'"), problems.toString());
    }

    @Test
    void validateReportsAmbiguousConstructor() {
        this.container.registerSingleton(TwoConstructors.class);

        var problems = this.container.validate();

        assertEquals(1, problems.size(), problems.toString());
        assertTrue(problems.getFirst().contains("@DependencyConstructor"), problems.toString());
    }

    @Test
    void validateChecksAdditionalRoots() {
        // Nothing registered: the class will be resolved without a registration
        assertEquals(List.of(), this.container.validate());
        assertEquals(List.of("Circular dependency: CycleA -> CycleB -> CycleC -> CycleA"), this.container.validate(CycleA.class));
    }

    @Test
    void validateDoesNotFollowSuppliersOrObjects() {
        // What these depend on can't be seen, so they are dead ends rather than problems
        this.container
                .registerFactory(Service.class, () -> this.container.resolve(ServiceUser.class))
                .registerSingleton(Simple.class, new Simple())
                .registerSingleton(NeedsSimple.class);

        assertEquals(List.of(), this.container.validate());
    }
}
