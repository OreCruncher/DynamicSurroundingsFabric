package org.orecruncher.dsurround.effects.aurora;

import org.jetbrains.annotations.Nullable;

/**
 * How far each arc's travelling wave, curls and rays have moved along it, frame by frame. Their speeds change through
 * the night (see {@link RealisticAurora.Moment#speed}), so where they are can't be worked out from the time and a
 * speed: the time is large, and the slightest change of speed would send them leaping. Instead each frame moves them on
 * by how far they go in the time since the last.
 * <p>
 * Each is kept within the length its pattern repeats at, so it stays precise as a float however long the aurora shows:
 * the wave within its wavelength, the curls within theirs, and the rays within {@link #RAY_WRAP}.
 */
public final class RealisticAuroraMotion {

    /**
     * The length the rays' pattern repeats at along an arc, in kilometres: both of the shader's ray noises repeat
     * within it (the noise texture's size of cells of {@link RealisticAuroraRenderer#FINE_RAY} and of
     * {@link RealisticAuroraRenderer#COARSE_RAY} kilometres)
     */
    static final double RAY_WRAP = RealisticAuroraRenderer.NOISE_SIZE * 16.1D;
    /**
     * The most time moved on in one frame, in seconds: after a pause or a stall they carry on from where they were
     */
    static final float MAX_STEP = 0.25F;
    /**
     * How fast the ray field's rays drift east when calm, in kilometres a second, and the length their pattern repeats
     * at: the noise texture's size of cells of {@link RealisticAuroraRenderer#FIELD_CELL} kilometres
     */
    static final float FIELD_SPEED = 1.5F;
    static final double FIELD_WRAP = RealisticAuroraRenderer.NOISE_SIZE * (double) RealisticAuroraRenderer.FIELD_CELL;

    private final float[] wave = new float[RealisticAurora.MAX_ARCS];
    private final float[] curl = new float[RealisticAurora.MAX_ARCS];
    private final float[] ray = new float[RealisticAurora.MAX_ARCS];
    private float field;
    private float lastTime = Float.NaN;
    // The aurora they are moving for: another's start over
    @Nullable
    private RealisticAurora.Arc first;
    private int count;

    /**
     * Moves them on to {@code time}
     *
     * @param aurora the aurora being drawn
     * @param speed  how much faster than when calm they move now
     * @param time   seconds
     */
    public void advance(RealisticAurora aurora, float speed, float time) {
        var arcs = aurora.arcs();
        int n = Math.min(arcs.size(), RealisticAurora.MAX_ARCS);
        if (n == 0)
            return;
        if (arcs.getFirst() != this.first || n != this.count) {
            this.first = arcs.getFirst();
            this.count = n;
            for (int i = 0; i < n; i++) {
                this.wave[i] = 0F;
                this.curl[i] = 0F;
                this.ray[i] = wrap(-arcs.get(i).rayOffset(), RAY_WRAP);
            }
            this.field = 0F;
            this.lastTime = time;
        }

        float step = Math.clamp(time - this.lastTime, 0F, MAX_STEP);
        this.lastTime = time;
        for (int i = 0; i < n; i++) {
            var arc = arcs.get(i);
            this.wave[i] = wrap(this.wave[i] + (double) arc.waveSpeed() * speed * step, arc.waveWavelength());
            this.curl[i] = wrap(this.curl[i] + (double) arc.raySpeed() * speed * step, RealisticAurora.CURL_WAVELENGTH);
            this.ray[i] = wrap(this.ray[i] + (double) arc.raySpeed() * speed * step, RAY_WRAP);
        }
        this.field = wrap(this.field + (double) FIELD_SPEED * speed * step, FIELD_WRAP);
    }

    /**
     * How far the ray field's rays have drifted east, in kilometres, within {@link #FIELD_WRAP}
     */
    public float field() {
        return this.field;
    }

    /**
     * How far arc i's travelling wave has moved east, in kilometres, within its wavelength
     */
    public float wave(int i) {
        return this.wave[i];
    }

    /**
     * How far arc i's curls have moved east, in kilometres, within their wavelength
     */
    public float curl(int i) {
        return this.curl[i];
    }

    /**
     * How far arc i's rays have moved east, in kilometres, from where its pattern starts, within {@link #RAY_WRAP}
     */
    public float ray(int i) {
        return this.ray[i];
    }

    private static float wrap(double value, double length) {
        double wrapped = value % length;
        return (float) (wrapped < 0D ? wrapped + length : wrapped);
    }
}
