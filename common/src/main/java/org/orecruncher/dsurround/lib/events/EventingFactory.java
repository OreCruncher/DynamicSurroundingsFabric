package org.orecruncher.dsurround.lib.events;

import com.google.common.base.Suppliers;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.logging.ModLog;
import org.orecruncher.dsurround.lib.reflection.HandleCache;
import org.orecruncher.dsurround.lib.reflection.IMethodCallHandler;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public final class EventingFactory {

    private static final Supplier<IModLog> LOGGER = Suppliers.memoize(() -> ModLog.createChild(ContainerManager.resolve(IModLog.class), "EventingFactory"));

    private EventingFactory() {
    }

    /**
     * Creates an event using custom callback processing with prioritization.
     *
     * @param typeGetter Convenience to obtain the Class of T
     * @param <IHandler> Event interface to be modeled for the event
     * @return Newly constructed event reference
     */
    @SafeVarargs
    public static <IHandler> IPhasedEvent<IHandler> createPrioritizedEvent(IHandler... typeGetter) {
        return createPhasedEvent(HandlerPriority.PHASED_ORDERING, typeGetter);
    }

    @SuppressWarnings("unchecked")
    public static <IHandler> IPhasedEvent<IHandler> createPhasedEvent(EventPhases eventPhases, IHandler... typeGetter) {
        return createPhasedEvent(eventPhases, (Class<IHandler>) typeGetter.getClass().getComponentType());
    }

    public static <IHandler> IPhasedEvent<IHandler> createPhasedEvent(EventPhases eventPhases, Class<IHandler> clazz) {
        return PhasedEvent.of(eventPhases, createProxy(clazz));
    }

    /**
     * Creates an event with custom callback processing
     *
     * @param typeGetter Convenience to obtain the Class of T
     * @param <IHandler> The type of entity that will be passed into callback handlers
     * @return Newly constructed event reference
     */
    @SuppressWarnings("unchecked")
    public static <IHandler> IEvent<IHandler> createEvent(IHandler... typeGetter) {
        return createEvent((Class<IHandler>) typeGetter.getClass().getComponentType());
    }

    /**
     * Creates an event with custom callback processing
     *
     * @param clazz      Class definition for the IHandler interface
     * @param <IHandler> The type of entity that will be passed into callback handlers
     * @return Newly constructed event reference
     */
    public static <IHandler> IEvent<IHandler> createEvent(Class<IHandler> clazz) {
        return Event.of(createProxy(clazz));
    }

    @SuppressWarnings("unchecked")
    private static <IHandler> Function<List<IHandler>, IHandler> createProxy(Class<IHandler> clazz) {
        try {
            var methodHandle = HandleCache.forFunctionalInterface(clazz);
            return listeners -> {
                // Don't optimize for the single event handler case. Though it would be more optimal, the
                // exception handling behavior would be different and consistency is important.
                InvocationHandler handler;
                if (listeners.isEmpty()) {
                    // No listeners so it's a noop
                    handler = NullEventLoop.INSTANCE;
                } else {
                    // One or more so do the loop
                    var name = clazz.getSimpleName() + " Event Loop";
                    handler = new EventLoop<>(name, listeners, methodHandle);
                }
                return (IHandler) Proxy.newProxyInstance(EventingFactory.class.getClassLoader(), new Class[]{clazz}, handler);
            };
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private record NullEventLoop() implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return null;
        }

        public @NotNull String toString() {
            return "Null Event Loop";
        }

        static final NullEventLoop INSTANCE = new NullEventLoop();
    }

    private record EventLoop<IHandler>(String name, List<IHandler> listeners,
                                       IMethodCallHandler methodHandle) implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method ignored, Object[] args) {
            for (var handler : this.listeners)
                try {
                    // Exceptions should be handled within the event handler. If an exception escapes
                    // consider it fatal. (The only logic that should be hooking these events are
                    // Dynamic Surroundings.)
                    this.methodHandle.invoke(handler, args);
                } catch (Throwable ex) {
                    LOGGER.get().error(ex, "[%s] Error invoking event handler '%s'", this.name(), handler.getClass().getName());
                    throw ex;
                }
            // Not used
            return null;
        }

        public @NotNull String toString() {
            return "%s (%d handlers)".formatted(this.name(), this.listeners().size());
        }
    }
}
