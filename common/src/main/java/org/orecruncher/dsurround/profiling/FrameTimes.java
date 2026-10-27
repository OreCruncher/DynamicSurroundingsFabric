package org.orecruncher.dsurround.profiling;

import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.effects.RenderTimer;

import java.util.Arrays;

/**
 * The game's frames, for the recording /dsprofile is making (see {@link FramesEvent}): how long each frame took, from
 * the start of one frame's drawing to the next's, and how long drawing the frame took, on the GPU and the CPU, by a
 * {@link RenderTimer} around the game's whole drawing of it (the world and the screen over it). The mod's effects'
 * timers are measured the same way, so each is a share of the frame's. While nothing records, it does nothing. Uses
 * nothing from {@code jdk.jfr}, so the hook in the game's frame can call it in a Java without that module.
 */
public final class FrameTimes {

    /**
     * Drawing a frame, the whole of it
     */
    static final RenderTimer RENDER_TIMER = new RenderTimer("Frame");

    private static volatile boolean recording;
    // Render thread only: when the last frame's drawing began, by System.nanoTime; 0 for none yet
    private static long lastBegin;
    // Render thread only: whether the timer was begun this frame, so it is ended even if recording stopped meanwhile
    private static boolean timing;

    // Frame lengths in ms since the last take, guarded by LOCK
    private static final Object LOCK = new Object();
    private static float[] lengths = new float[512];
    private static int count;

    private FrameTimes() {
    }

    /**
     * A frame's drawing begins, on the render thread
     */
    public static void begin() {
        if (!recording)
            return;
        long now = System.nanoTime();
        if (lastBegin != 0)
            add((now - lastBegin) / 1_000_000F);
        lastBegin = now;
        RENDER_TIMER.begin();
        timing = true;
    }

    /**
     * A frame's drawing ends, on the render thread
     */
    public static void end() {
        if (!timing)
            return;
        timing = false;
        RENDER_TIMER.end();
    }

    /**
     * A frame's length, ms
     */
    static void add(float ms) {
        synchronized (LOCK) {
            if (count == lengths.length)
                lengths = Arrays.copyOf(lengths, count * 2);
            lengths[count++] = ms;
        }
    }

    static void start() {
        synchronized (LOCK) {
            count = 0;
        }
        // Read on the render thread only once recording is seen, so the first frame after starts afresh
        lastBegin = 0;
        recording = true;
    }

    static void stop() {
        recording = false;
    }

    /**
     * The frames since the last take, and the drawing's averages now; null if there were none
     */
    static @Nullable Second take() {
        float[] taken;
        synchronized (LOCK) {
            if (count == 0)
                return null;
            taken = Arrays.copyOf(lengths, count);
            count = 0;
        }
        Arrays.sort(taken);
        double total = 0;
        for (var length : taken)
            total += length;
        return new Second(taken.length, total / taken.length, percentile(taken, 0.5), percentile(taken, 0.95),
                taken[taken.length - 1], RENDER_TIMER.getMSecs(), RENDER_TIMER.getCpuMSecs());
    }

    private static float percentile(float[] sorted, double share) {
        return sorted[Math.min(sorted.length - 1, (int) (share * sorted.length))];
    }

    /**
     * A second's frames
     *
     * @param frames how many
     * @param mean   their mean length, ms
     * @param p50    their median length, ms
     * @param p95    the length 95% of them were within, ms
     * @param max    the longest, ms
     * @param gpu    drawing a frame on the GPU, ms, the running average
     * @param cpu    drawing a frame on the CPU, ms, the running average
     */
    record Second(int frames, double mean, double p50, double p95, double max, double gpu, double cpu) {
    }
}
