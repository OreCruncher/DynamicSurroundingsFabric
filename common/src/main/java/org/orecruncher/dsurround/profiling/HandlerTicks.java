package org.orecruncher.dsurround.profiling;

import org.jetbrains.annotations.Nullable;

/**
 * Where the client tick's handlers say how long each took, for the recording /dsprofile is making (see
 * {@link HandlerTickEvent}); while nothing records, what they say goes nowhere. Uses nothing from {@code jdk.jfr}, so
 * the handlers can call it in a Java without that module.
 */
public final class HandlerTicks {

    @FunctionalInterface
    interface Sink {
        void ticked(long tick, String handler, long nanos, boolean ran);
    }

    private static volatile @Nullable Sink sink;

    private HandlerTicks() {
    }

    /**
     * One handler's time in one tick, from the client thread
     *
     * @param ran whether the handler had work this tick
     */
    public static void ticked(long tick, String handler, long nanos, boolean ran) {
        var current = sink;
        if (current != null)
            current.ticked(tick, handler, nanos, ran);
    }

    static void start(Sink recording) {
        sink = recording;
    }

    static void stop() {
        sink = null;
    }
}
