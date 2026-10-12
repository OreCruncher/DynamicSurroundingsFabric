package org.orecruncher.dsurround.effects;

import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;
import org.orecruncher.dsurround.lib.math.ITimer;
import org.orecruncher.dsurround.lib.math.TimerEMA;

/**
 * How long the GPU takes to draw something, for the diagnostics overlay: the timers there measure the CPU, which only
 * hands the drawing over. A GPU timestamp is taken before and after, and the difference averaged.
 * <p>
 * Timestamps rather than an elapsed time query: only one of those can run at a time, and vanilla runs one around
 * the whole frame for its GPU figure. The results come a frame or more later; waiting for them would hold the CPU up
 * until the GPU caught up, so a few frames' are kept, and read once ready. Render thread only. Does nothing where timer
 * queries aren't supported.
 */
public final class GpuTimer implements ITimer {

    // Frames whose results may still be on the way
    private static final int FRAMES = 4;

    private final TimerEMA average;
    private final int[] starts = new int[FRAMES];
    private final int[] ends = new int[FRAMES];
    private final boolean[] pending = new boolean[FRAMES];
    private int frame;
    private boolean measuring;
    // Not yet known until there is a GL context: null, then whether queries could be made
    private Boolean supported;

    public GpuTimer(String name) {
        this.average = new TimerEMA(name);
    }

    /**
     * Marks the start of what is measured
     */
    public void begin() {
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
        if (!this.measuring)
            return;
        GL33.glQueryCounter(this.ends[this.frame], GL33.GL_TIMESTAMP);
        this.measuring = false;
        this.pending[this.frame] = true;
        this.frame = (this.frame + 1) % FRAMES;
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
                this.average.update(end - start);
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

    @Override
    public double getMSecs() {
        return this.average.getMSecs();
    }

    @Override
    public String toString() {
        return this.average.toString();
    }
}
