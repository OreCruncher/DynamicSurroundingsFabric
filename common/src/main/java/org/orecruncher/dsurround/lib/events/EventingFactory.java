package org.orecruncher.dsurround.lib.events;

import com.google.common.base.Suppliers;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.logging.LogThrottle;
import org.orecruncher.dsurround.lib.logging.ModLog;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Creates events. Each event is given its invoker factory: a function from the registered handlers to a single
 * handler that calls them all. Mark the handler interface {@link GenerateInvoker} and the build generates the
 * factory, so the dispatch loop is never written by hand.
 */
public final class EventingFactory {

    // A failing handler is logged at most this many times, and handler failures at most TOTAL_FAILURE_LOGS times
    // overall, so a handler that throws on every tick or frame can't flood the log
    private static final int FAILURE_LOGS_PER_HANDLER = 3;
    private static final int TOTAL_FAILURE_LOGS = 50;

    // Lazy, as events are created in static initializers. Library.LOGGER rather than the container's logger, so
    // reporting a failure can't itself fail
    private static final Supplier<LogThrottle<Object>> FAILURES = Suppliers.memoize(() -> new LogThrottle<>(
            ModLog.createChild(Library.LOGGER, "EventingFactory"),
            "event handler errors", null, FAILURE_LOGS_PER_HANDLER, TOTAL_FAILURE_LOGS));

    private EventingFactory() {
    }

    /**
     * Creates an event whose handlers are registered with a {@link HandlerPriority} and called highest first.
     */
    public static <IHandler> IPhasedEvent<IHandler> createPrioritizedEvent(Function<List<IHandler>, IHandler> invokerFactory) {
        return createPhasedEvent(HandlerPriority.PHASED_ORDERING, invokerFactory);
    }

    /**
     * Creates an event whose handlers are registered with one of {@code eventPhases} and called in phase order.
     */
    public static <IHandler> IPhasedEvent<IHandler> createPhasedEvent(EventPhases eventPhases, Function<List<IHandler>, IHandler> invokerFactory) {
        return new PhasedEvent<>(eventPhases.getPhases(), invokerFactory);
    }

    /**
     * Creates an event whose handlers are called in registration order.
     */
    public static <IHandler> IEvent<IHandler> createEvent(Function<List<IHandler>, IHandler> invokerFactory) {
        return new Event<>(invokerFactory);
    }

    /**
     * Called by generated invokers when a handler throws. Logs it (limited, see above) and returns, so the
     * remaining handlers still run. A {@link VirtualMachineError} (out of memory, stack overflow) is rethrown.
     *
     * @param eventName the handler interface's name
     * @param handler   the handler that threw
     */
    public static void handlerFailed(String eventName, Object handler, Throwable t) {
        if (t instanceof VirtualMachineError fatal)
            throw fatal;
        FAILURES.get().error(handler, t, "[%s] Handler %s threw an exception", eventName, describeHandler(handler));
    }

    /**
     * A readable name for a handler. Lambdas and method references have names like "Client$$Lambda/0x1234"; the
     * declaring class is the useful part.
     */
    static String describeHandler(Object handler) {
        var name = handler.getClass().getName();
        var lambda = name.indexOf("$$Lambda");
        return lambda < 0 ? name : name.substring(0, lambda) + " (lambda)";
    }
}
