package org.orecruncher.dsurround.lib.events;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for events and the invokers the build generates for them (the handler interfaces here are marked
 * {@link GenerateInvoker}, so these also check the processor runs on the mod's build).
 */
public class EventTests {

    @GenerateInvoker
    @FunctionalInterface
    public interface IRecord {
        // Required by @GenerateInvoker; the tests create a fresh event each time instead
        IPhasedEvent<IRecord> EVENT = EventingFactory.createPrioritizedEvent(EventTests_IRecordInvoker::create);

        void onRecord(List<String> log, String value);
    }

    @GenerateInvoker
    @FunctionalInterface
    public interface ICount {
        IEvent<ICount> EVENT = EventingFactory.createEvent(EventTests_ICountInvoker::create);

        void onCount();
    }

    private static IPhasedEvent<IRecord> prioritized() {
        return EventingFactory.createPrioritizedEvent(EventTests_IRecordInvoker::create);
    }

    private static IEvent<IRecord> plain() {
        return EventingFactory.createEvent(EventTests_IRecordInvoker::create);
    }

    private static List<String> raise(IEvent<IRecord> event) {
        var log = new ArrayList<String>();
        event.invoker().onRecord(log, "x");
        return log;
    }

    // ---- Ordering --------------------------------------------------------------------------------------------

    @Test
    void plainEventCallsHandlersInRegistrationOrder() {
        var event = plain();
        event.register((log, v) -> log.add("a"));
        event.register((log, v) -> log.add("b"));
        event.register((log, v) -> log.add("c"));

        assertEquals(List.of("a", "b", "c"), raise(event));
    }

    @Test
    void handlersReceiveTheArguments() {
        var event = plain();
        event.register((log, v) -> log.add("got " + v));

        assertEquals(List.of("got x"), raise(event));
    }

    @Test
    void prioritizedEventCallsHighestFirst() {
        var event = prioritized();
        event.register((log, v) -> log.add("low"), HandlerPriority.LOW);
        event.register((log, v) -> log.add("very high"), HandlerPriority.VERY_HIGH);
        event.register((log, v) -> log.add("normal"));
        event.register((log, v) -> log.add("very low"), HandlerPriority.VERY_LOW);
        event.register((log, v) -> log.add("high"), HandlerPriority.HIGH);

        assertEquals(List.of("very high", "high", "normal", "low", "very low"), raise(event));
    }

    @Test
    void samePriorityKeepsRegistrationOrder() {
        var event = prioritized();
        event.register((log, v) -> log.add("high 1"), HandlerPriority.HIGH);
        event.register((log, v) -> log.add("low 1"), HandlerPriority.LOW);
        event.register((log, v) -> log.add("high 2"), HandlerPriority.HIGH);
        event.register((log, v) -> log.add("low 2"), HandlerPriority.LOW);
        event.register((log, v) -> log.add("high 3"), HandlerPriority.HIGH);

        assertEquals(List.of("high 1", "high 2", "high 3", "low 1", "low 2"), raise(event));
    }

    @Test
    void customPhasesAreFollowed() {
        var first = EventPhase.of("test", "first");
        var last = EventPhase.of("test", "last");
        IPhasedEvent<IRecord> event = EventingFactory.createPhasedEvent(
                EventPhase.phaseOrderingOf(first, EventPhase.DEFAULT, last), EventTests_IRecordInvoker::create);
        event.register((log, v) -> log.add("last"), last);
        event.register((log, v) -> log.add("default"));
        event.register((log, v) -> log.add("first"), first);

        assertEquals(List.of("first", "default", "last"), raise(event));
    }

    @Test
    void unknownPhaseIsRejected() {
        var event = prioritized();

        var e = assertThrows(IllegalArgumentException.class,
                () -> event.register((log, v) -> {}, EventPhase.of("not", "a", "priority")));
        assertTrue(e.getMessage().contains("not.a.priority"), e.getMessage());
    }

    @Test
    void phaseOrderingMustIncludeDefaultAndHaveNoDuplicates() {
        var phase = EventPhase.of("test", "phase");

        assertThrows(IllegalStateException.class, () -> EventPhase.phaseOrderingOf(phase));
        assertThrows(IllegalStateException.class, () -> EventPhase.phaseOrderingOf(phase, EventPhase.DEFAULT, EventPhase.of("test", "phase")));
    }

    // ---- Registration and invokers ---------------------------------------------------------------------------

    @Test
    void eventWithoutHandlersDoesNothing() {
        assertEquals(List.of(), raise(plain()));
        assertEquals(List.of(), raise(prioritized()));
    }

    @Test
    void registrationAfterInvokerIsPickedUp() {
        var event = prioritized();
        event.register((log, v) -> log.add("a"));
        assertEquals(List.of("a"), raise(event));

        event.register((log, v) -> log.add("b"));

        assertEquals(List.of("a", "b"), raise(event));
    }

    @Test
    void registeringWhileRaisingTakesEffectNextTime() {
        var event = plain();
        event.register((log, v) -> {
            log.add("registering");
            if (log.size() == 1 && v.equals("x"))
                event.register((l, value) -> l.add("added"));
        });

        assertEquals(List.of("registering"), raise(event), "not called while the event is being raised");
        assertTrue(raise(event).contains("added"));
    }

    @Test
    void sameHandlerCanBeRegisteredTwice() {
        var event = plain();
        IRecord handler = (log, v) -> log.add("h");
        event.register(handler);
        event.register(handler);

        assertEquals(List.of("h", "h"), raise(event));
    }

    @Test
    void nullHandlerIsRejected() {
        assertThrows(NullPointerException.class, () -> plain().register(null));
        assertThrows(NullPointerException.class, () -> prioritized().register(null, HandlerPriority.HIGH));
    }

    @Test
    void invokerIsAPlainObject() {
        // Regression: the reflective proxy passed toString/hashCode/equals to the handlers
        var calls = new AtomicInteger();
        IEvent<ICount> event = EventingFactory.createEvent(EventTests_ICountInvoker::create);
        event.register(calls::incrementAndGet);

        var invoker = event.invoker();
        assertNotNull(invoker.toString());
        invoker.hashCode();
        assertEquals(invoker, invoker);

        assertEquals(0, calls.get());
    }

    // ---- Exceptions ------------------------------------------------------------------------------------------

    @Test
    void throwingHandlerDoesNotStopTheOthers() {
        var event = prioritized();
        event.register((log, v) -> log.add("before"), HandlerPriority.HIGH);
        event.register((log, v) -> {
            throw new IllegalStateException("boom");
        });
        event.register((log, v) -> log.add("after"), HandlerPriority.LOW);

        assertEquals(List.of("before", "after"), raise(event));
    }

    @Test
    void throwingHandlerKeepsWorkingEveryTime() {
        // Many failures: logging is limited, and the other handlers keep running
        var calls = new AtomicInteger();
        IEvent<ICount> event = EventingFactory.createEvent(EventTests_ICountInvoker::create);
        event.register(() -> {
            throw new IllegalStateException("every time");
        });
        event.register(calls::incrementAndGet);

        for (int i = 0; i < 200; i++)
            event.invoker().onCount();

        assertEquals(200, calls.get());
    }

    @Test
    void fatalErrorsAreNotSwallowed() {
        IEvent<ICount> event = EventingFactory.createEvent(EventTests_ICountInvoker::create);
        event.register(() -> {
            throw new OutOfMemoryError("pretend");
        });

        assertThrows(OutOfMemoryError.class, () -> event.invoker().onCount());
    }

    @Test
    void lambdaHandlersAreDescribedByTheirDeclaringClass() {
        IRecord lambda = (log, v) -> {};

        assertEquals(EventTests.class.getName() + " (lambda)", EventingFactory.describeHandler(lambda));
        assertEquals("java.lang.String", EventingFactory.describeHandler("not a lambda"));
    }

    // ---- Threads ---------------------------------------------------------------------------------------------

    @Test
    void concurrentRegistrationKeepsEveryHandler() throws Exception {
        var event = prioritized();
        var calls = Collections.synchronizedList(new ArrayList<Integer>());
        int threads = 8;
        int perThread = 250;

        var pool = Executors.newFixedThreadPool(threads);
        var start = new CountDownLatch(1);
        try {
            for (int t = 0; t < threads; t++) {
                int thread = t;
                pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < perThread; i++) {
                        int id = thread * perThread + i;
                        event.register((log, v) -> calls.add(id), i % 2 == 0 ? HandlerPriority.HIGH : HandlerPriority.LOW);
                        // Raising while others register must not fail
                        event.invoker();
                    }
                    return null;
                });
            }
            start.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }

        raise(event);
        assertEquals(threads * perThread, calls.size());
        assertEquals(threads * perThread, calls.stream().distinct().count());
    }
}
