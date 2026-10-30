package org.orecruncher.dsurround.effects;

import com.mojang.blaze3d.systems.GpuQueryPool;
import com.mojang.blaze3d.systems.RenderSystem;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.lib.math.EMA;
import org.orecruncher.dsurround.lib.math.ITimer;
import org.orecruncher.dsurround.lib.math.TimerEMA;

/**
 * How long drawing something takes, for the diagnostics overlay: on the CPU, building and handing over the drawing
 * (vertices, uploads, state), and on the GPU, doing it. The two don't add up to a frame's time: the GPU works on a
 * frame while the CPU builds the next. A GPU timestamp is taken before and after, and the difference averaged.
 * <p>
 * Timestamps rather than an elapsed time query: only one of those can run at a time, and vanilla runs one around
 * the whole frame for its GPU figure. The results come a frame or more later; waiting for them would hold the CPU up
 * until the GPU caught up, so a few frames' are kept, and read once ready. The timestamps go through the device's query
 * pool, so they work whichever graphics backend is in use. Render thread only. Does nothing where timestamp queries
 * aren't supported.
 */
public final class RenderTimer implements ITimer {

    // Frames whose results may still be on the way
    private static final int FRAMES = 4;
    // How long since it last measured before the diagnostics overlay stops showing it
    private static final long STALE_AFTER_NANOS = 1_000_000_000L;

    private final String name;
    private final boolean perTick;
    private final TimerEMA cpu;
    private final TimerEMA gpu;
    // How many of what it draws there were a frame, averaged as the times are; only for timers given a count
    private final EMA count;
    private boolean counted;
    // A start and an end timestamp for each frame, in that order
    @Nullable
    private GpuQueryPool timestamps;
    private final boolean[] pending = new boolean[FRAMES];
    private int frame;
    private boolean measuring;
    // When the CPU started, by System.nanoTime; 0 if not measuring
    private long cpuStart;
    // When it last measured, by System.nanoTime; 0 if it never has
    private long lastMeasured;
    // Not yet known until there is a device: null, then whether queries could be made
    private Boolean supported;

    public RenderTimer(String name) {
        this(name, false);
    }

    /**
     * @param perTick whether what it times runs once a client tick rather than once a frame (the clouds' field): its
     *                averages are then a tick's, which recordings note so as not to set them against a frame's
     */
    public RenderTimer(String name, boolean perTick) {
        this.name = name;
        this.perTick = perTick;
        this.cpu = new TimerEMA(name);
        this.gpu = new TimerEMA(name);
        this.count = new EMA(name);
    }

    /**
     * Notes how many of what it draws (drops, spots) there were this frame, so recordings show how much there was to
     * draw beside how long it took: two recordings only compare when the work matches. Once a frame, if at all.
     */
    public void count(int items) {
        this.count.update(items);
        this.counted = true;
    }

    /**
     * How many of what it draws there were a frame, on average; NaN for a timer that doesn't count
     */
    public double getCount() {
        return this.counted ? this.count.get() : Double.NaN;
    }

    /**
     * Marks the start of what is measured
     */
    public void begin() {
        this.cpuStart = System.nanoTime();
        if (!this.isSupported())
            return;
        this.collect();
        // Still waiting on this frame's from a few frames ago: the GPU is behind, so this one isn't measured
        if (this.pending[this.frame])
            return;
        RenderSystem.getDevice().createCommandEncoder().writeTimestamp(this.timestamps, this.frame * 2);
        this.measuring = true;
    }

    /**
     * Marks the end of what is measured, if {@link #begin} started measuring
     */
    public void end() {
        if (this.cpuStart != 0) {
            long now = System.nanoTime();
            this.cpu.update(now - this.cpuStart);
            this.cpuStart = 0;
            this.lastMeasured = now;
        }
        if (!this.measuring)
            return;
        RenderSystem.getDevice().createCommandEncoder().writeTimestamp(this.timestamps, this.frame * 2 + 1);
        this.measuring = false;
        this.pending[this.frame] = true;
        this.frame = (this.frame + 1) % FRAMES;
    }

    /**
     * Whether it hasn't measured in the last second: what it measures isn't being drawn, and its average is left over
     * from when it was
     */
    public boolean isStale() {
        return this.lastMeasured == 0 || System.nanoTime() - this.lastMeasured >= STALE_AFTER_NANOS;
    }

    /**
     * Takes in the results that have come, without waiting for any
     */
    private void collect() {
        for (int i = 0; i < FRAMES; i++) {
            if (!this.pending[i])
                continue;
            var end = this.timestamps.getValue(i * 2 + 1);
            if (end.isEmpty())
                continue;
            var start = this.timestamps.getValue(i * 2);
            if (start.isEmpty())
                continue;
            this.pending[i] = false;
            if (end.getAsLong() >= start.getAsLong())
                this.gpu.update(end.getAsLong() - start.getAsLong());
        }
    }

    private boolean isSupported() {
        if (this.supported == null) {
            try {
                this.timestamps = RenderSystem.getDevice().createTimestampQueryPool(FRAMES * 2);
            } catch (RuntimeException e) {
                this.timestamps = null;
            }
            this.supported = this.timestamps != null;
        }
        return this.supported;
    }

    /**
     * Whether it times something run once a client tick rather than once a frame
     */
    public boolean isPerTick() {
        return this.perTick;
    }

    /**
     * What it times, as the diagnostics overlay names it
     */
    public String name() {
        return this.name;
    }

    /**
     * The GPU's time, in milliseconds
     */
    @Override
    public double getMSecs() {
        return this.gpu.getMSecs();
    }

    /**
     * The GPU's longest time lately, in milliseconds
     */
    public double getPeakMSecs() {
        return this.gpu.getPeakMSecs();
    }

    /**
     * The CPU's time, in milliseconds
     */
    public double getCpuMSecs() {
        return this.cpu.getMSecs();
    }

    /**
     * The CPU's longest time lately, in milliseconds
     */
    public double getCpuPeakMSecs() {
        return this.cpu.getPeakMSecs();
    }

    @Override
    public String toString() {
        // Each as its average and peak. No GPU figures where timer queries aren't supported
        var cpu = String.format("%s:%7.3f,%7.3f", this.name, this.cpu.getMSecs(), this.cpu.getPeakMSecs());
        if (Boolean.FALSE.equals(this.supported))
            return cpu + " /    n/a";
        return String.format("%s /%7.3f,%7.3f", cpu, this.gpu.getMSecs(), this.gpu.getPeakMSecs());
    }
}
