package org.orecruncher.dsurround.profiling;

import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.effects.aurora.Aurora;

/**
 * The classic aurora as it was drawn each frame, for the recording /dsprofile is making (see {@link AuroraEvent}): its
 * shape, which the night picks, and how much of the screen it covered, which its GPU time follows. While nothing
 * records, the renderer doesn't work out its coverage. Uses nothing from {@code jdk.jfr}, so the renderer can call it in
 * a Java without that module.
 */
public final class AuroraSamples {

    @FunctionalInterface
    interface Sink {
        void drawn(Aurora aurora, float strength, double pixels, int screenPixels);
    }

    private static volatile @Nullable Sink sink;

    private AuroraSamples() {
    }

    /**
     * Whether a recording wants the aurora's frames; asked once a frame
     */
    public static boolean isRecording() {
        return sink != null;
    }

    /**
     * One frame's aurora, from the render thread.
     *
     * @param strength     how strongly it was drawn, 0 to 1 (the shader's alpha)
     * @param pixels       the pixels its quads covered, counted as often as they overlap
     * @param screenPixels the screen's
     */
    public static void drawn(Aurora aurora, float strength, double pixels, int screenPixels) {
        var current = sink;
        if (current != null)
            current.drawn(aurora, strength, pixels, screenPixels);
    }

    static void start(Sink recording) {
        sink = recording;
    }

    static void stop() {
        sink = null;
    }
}
