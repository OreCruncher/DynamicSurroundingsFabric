package org.orecruncher.dsurround.lib.events;

import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.lib.reflection.ReflectionHelper;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.function.Function;

public final class EventingFactory {
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
            var method = ReflectionHelper.findSamMethod(clazz);
            var methodHandle = MethodHandles.lookup().unreflect(method);
            return listeners -> {
                // If there is only one listener it can be directly accessed.
                if (listeners.size() == 1) {
                    return listeners.getFirst();
                }

                InvocationHandler handler;
                // If there are no listeners return the null event loop
                if (listeners.isEmpty()) {
                    handler = NullEventLoop.INSTANCE;
                } else {
                    // More than one listener, so do the loop
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
                                       MethodHandle methodHandle) implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method ignored, Object[] args) throws Throwable {
            for (var handler : this.listeners)
                // It is slightly faster to do it this way than to pre-create an array of bound
                // method handles. Not sure why.
                this.methodHandle.bindTo(handler).invokeWithArguments(args);
            return null;
        }

        public @NotNull String toString() {
            return this.name;
        }
    }
}
