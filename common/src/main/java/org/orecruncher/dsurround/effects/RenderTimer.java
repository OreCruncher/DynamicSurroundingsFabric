package org.orecruncher.dsurround.effects;

import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;
import org.orecruncher.dsurround.lib.math.ITimer;
import org.orecruncher.dsurround.lib.math.TimerEMA;

/**
 * How long drawing something takes, for the diagnostics overlay: on the CPU, building and handing over the drawing
 * (vertices, uploads, state), and on the GPU, doing it. The two don't add up to a frame's time: the GPU works on a
 * frame while the CPU builds the next. A GPU timestamp is taken before and after, and the difference averaged.
 * <p>
 * Timestamps rather than an elapsed time query: only one of those can run at a time, and vanilla runs one around
 * the whole frame for its GPU figure. The results come a frame or more later; waiting for them would hold the CPU up
 * until the GPU caught up, so a few frames' are kept, and read once ready. Render thread only. Does nothing where timer
 * queries aren't supported.
 */
public final class RenderTimer implements ITimer {

    // Frames whose results may still be on the way
    private static final int FRAMES = 4;
    // How long since it last measured before the diagnostics overlay stops showing it
    private static final long RECENT_NANOS = 1_000_000_000L;

    private final String name;
    private final TimerEMA cpu;
    private final TimerEMA gpu;
    private final int[] starts = new int[FRAMES];
    private final int[] ends = new int[FRAMES];
    private final boolean[] pending = new boolean[FRAMES];
    private int frame;
    private boolean measuring;
    // When the CPU started, by System.nanoTime; 0 if not measuring
    private long cpuStart;
    // When it last measured, by System.nanoTime; 0 if it never has
    private long lastMeasured;
    // Not yet known until there is a GL context: null, then whether queries could be made
    private Boolean supported;

    public RenderTimer(String name) {
        this.name = name;
        this.cpu = new TimerEMA(name);
        this.gpu = new TimerEMA(name);
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
        GL33.glQueryCounter(this.starts[this.frame], GL33.GL_TIMESTAMP);
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
        GL33.glQueryCounter(this.ends[this.frame], GL33.GL_TIMESTAMP);
        this.measuring = false;
        this.pending[this.frame] = true;
        this.frame = (this.frame + 1) % FRAMES;
    }

    /**
     * Whether it has measured in the last second: if not, what it measures isn't being drawn, and its average is
     * left over from when it was
     */
    public boolean isRecent() {
        return this.lastMeasured != 0 && System.nanoTime() - this.lastMeasured < RECENT_NANOS;
    }

    /**
     * Takes in the results that have come, without waiting for any
     */
    private void collect() {
        for (int i = 0; i < FRAMES; i++) {
            if (!this.pending[i] || GL15.glGetQueryObjecti(this.ends[i], GL15.GL_QUERY_RESULT_AVAILABLE) == 0)
                continue;
            long start = GL33.glGetQueryObjecti64(this.starts[i], GL15.GL_QUERY_RESULT);
            long end = GL33.glGetQueryObjecti64(this.ends[i], GL15.GL_QUERY_RESULT);
            this.pending[i] = false;
            if (end >= start)
                this.gpu.update(end - start);
        }
    }

    private boolean isSupported() {
        if (this.supported == null) {
            var caps = GL.getCapabilities();
            this.supported = caps.OpenGL33 || caps.GL_ARB_timer_query;
            if (this.supported) {
                GL15.glGenQueries(this.starts);
                GL15.glGenQueries(this.ends);
            }
        }
        return this.supported;
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
