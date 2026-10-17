package org.orecruncher.dsurround.effects.precipitation;

import com.mojang.blaze3d.vertex.Tesselator;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * Rain drops running down windows: each a bead on the outside of the glass that clings for a moment, then runs down in
 * a short rush, then clings again, as real drops do, leaving a wet trail that dries from the top down. Where the glass
 * ends it goes: over open air it drips off the edge, and onto a frame, a sill or the wall below it runs off and fades.
 * Kept in flat arrays like {@link SnowFlecks}, and drawn with the falling rain; drops added when it is full are left
 * out. Client thread only, which in Minecraft is also the render thread.
 */
public final class WindowStreaks {

    /**
     * Told where a drop runs off the bottom edge of a window over open air, to hang a drip there
     */
    @FunctionalInterface
    public interface Dripper {
        void drip(float x, float y, float z);
    }

    // Ticks a drop lasts at most; how long it takes to fade in, and to fade out once it has run off onto what is below
    static final int MAX_LIFETIME = 300;
    static final int FADE_IN = 3;
    static final int FADE_OUT = 10;
    // Ticks a new drop clings before it first runs: the largest drop the least, the smallest about twice that
    static final int FIRST_PAUSE_MIN = 10;
    static final int FIRST_PAUSE_RANGE = 30;
    // Ticks it clings between runs, and how far each run goes, in blocks
    static final int PAUSE_MIN = 4;
    static final int PAUSE_RANGE = 26;
    static final float RUN_MIN = 0.08F;
    static final float RUN_RANGE = 0.4F;
    // How fast it runs, in blocks a tick
    static final float RUN_SPEED = 0.08F;
    // How fast its trail dries, from the top down, in blocks a tick
    static final float TRAIL_DRY = 0.01F;
    // Just off the glass, and a little apart from each other, so neither the glass nor other drops show through
    static final float LIFT = 0.008F;
    static final float LIFT_RANGE = 0.004F;

    private final int capacity;
    // Where the bead is: x and z on the glass (lifted off it), y now and last tick; and the lowest it can go
    final float[] x, z, y, prevY, bottom;
    // The top of its wet trail, now and last tick
    final float[] trailTop, prevTrailTop;
    // How far it has left to go in this run, and the ticks it has left to cling before the next
    final float[] runLeft;
    final int[] pause;
    // How big the drop was, 0 to 1
    final float[] size;
    final int[] age, lifetime, light;
    // The side of the glass it is on (see SnowFlecks.FACE_WEST)
    final byte[] face;
    // Whether there is open air under the glass's bottom edge, so it drips off there
    final boolean[] dripsOff;
    int count;

    public WindowStreaks(int capacity) {
        this.capacity = capacity;
        this.x = new float[capacity];
        this.z = new float[capacity];
        this.y = new float[capacity];
        this.prevY = new float[capacity];
        this.bottom = new float[capacity];
        this.trailTop = new float[capacity];
        this.prevTrailTop = new float[capacity];
        this.runLeft = new float[capacity];
        this.pause = new int[capacity];
        this.size = new float[capacity];
        this.age = new int[capacity];
        this.lifetime = new int[capacity];
        this.light = new int[capacity];
        this.face = new byte[capacity];
        this.dripsOff = new boolean[capacity];
    }

    public int count() {
        return this.count;
    }

    public void clear() {
        this.count = 0;
    }

    /**
     * Adds a drop on a window
     *
     * @param x        where on the glass: the side is at {@code x} (west or east) or {@code z} (north or south)
     * @param face     the side of the glass it is on (see {@link SnowFlecks#FACE_WEST})
     * @param bottom   the lowest it can run to: where the glass ends
     * @param dripsOff whether there is open air under that edge, so it drips off there rather than running onto
     *                 whatever is below
     * @param size     how big the drop was, 0 to 1
     * @param light    its packed light
     */
    public void add(double x, double y, double z, byte face, double bottom, boolean dripsOff, float size, int light, RandomSource random) {
        if (this.count == this.capacity)
            return;
        int i = this.count++;
        float lift = LIFT + random.nextFloat() * LIFT_RANGE;
        this.x[i] = (float) x + SnowFlecks.normalX(face) * lift;
        this.z[i] = (float) z + SnowFlecks.normalZ(face) * lift;
        this.y[i] = this.prevY[i] = (float) y;
        this.trailTop[i] = this.prevTrailTop[i] = (float) y;
        this.bottom[i] = (float) Math.min(bottom, y);
        this.face[i] = face;
        this.dripsOff[i] = dripsOff;
        this.size[i] = Mth.clamp(size, 0F, 1F);
        this.pause[i] = FIRST_PAUSE_MIN + (int) ((1F - this.size[i]) * FIRST_PAUSE_MIN) + random.nextInt(FIRST_PAUSE_RANGE);
        this.runLeft[i] = RUN_MIN + random.nextFloat() * RUN_RANGE;
        this.age[i] = 0;
        this.lifetime[i] = MAX_LIFETIME;
        this.light[i] = light;
    }

    /**
     * Moves the drops a tick: those clinging wait, and those running run. One reaching the bottom edge of its window
     * drips off it, if there is open air below, and is gone; otherwise it runs off onto what is below, and fades.
     * Their trails dry.
     *
     * @param dripper told where drops drip off
     */
    public void tick(RandomSource random, Dripper dripper) {
        int i = 0;
        while (i < this.count) {
            if (++this.age[i] >= this.lifetime[i]) {
                this.remove(i);
                continue;
            }
            this.prevY[i] = this.y[i];
            this.prevTrailTop[i] = this.trailTop[i];

            if (this.y[i] <= this.bottom[i]) {
                if (this.dripsOff[i]) {
                    // Off the edge: a drip hangs there, and falls
                    dripper.drip(this.x[i], this.bottom[i], this.z[i]);
                    this.remove(i);
                    continue;
                }
                // Onto the frame, the sill or the wall: it runs off, and fades
                this.lifetime[i] = Math.min(this.lifetime[i], this.age[i] + FADE_OUT);
            } else if (this.pause[i] > 0) {
                this.pause[i]--;
            } else {
                float step = Math.min(RUN_SPEED, this.runLeft[i]);
                this.y[i] = Math.max(this.y[i] - step, this.bottom[i]);
                this.runLeft[i] -= step;
                if (this.runLeft[i] <= 0F) {
                    this.pause[i] = PAUSE_MIN + random.nextInt(PAUSE_RANGE);
                    this.runLeft[i] = RUN_MIN + random.nextFloat() * RUN_RANGE;
                }
            }
            this.trailTop[i] = Math.max(this.y[i], this.trailTop[i] - TRAIL_DRY);
            i++;
        }
    }

    // Moves the last drop into the gap, so the drops stay packed at the front of the arrays
    private void remove(int i) {
        int last = --this.count;
        this.x[i] = this.x[last];
        this.z[i] = this.z[last];
        this.y[i] = this.y[last];
        this.prevY[i] = this.prevY[last];
        this.bottom[i] = this.bottom[last];
        this.trailTop[i] = this.trailTop[last];
        this.prevTrailTop[i] = this.prevTrailTop[last];
        this.runLeft[i] = this.runLeft[last];
        this.pause[i] = this.pause[last];
        this.size[i] = this.size[last];
        this.age[i] = this.age[last];
        this.lifetime[i] = this.lifetime[last];
        this.light[i] = this.light[last];
        this.face[i] = this.face[last];
        this.dripsOff[i] = this.dripsOff[last];
    }

    /**
     * Draws the drops with the current shader, which should be {@link PrecipitationShaders#DROPS}. Untextured, like the
     * falling rain. Render thread only.
     */
    public void draw(Tesselator tesselator, Camera camera, float partialTick, RainColor rainColor) {
        PrecipitationRenderer.draw(this, tesselator, camera, partialTick, rainColor);
    }

    /**
     * How much of a drop is shown, 0 to 1: it fades in when new, and out at the end of its life
     */
    float fade(int i, float partialTick) {
        float age = this.age[i] + partialTick;
        return fadeFor(age, this.lifetime[i]);
    }

    static float fadeFor(float age, int lifetime) {
        return Mth.clamp(Math.min(age / FADE_IN, (lifetime - age) / FADE_OUT), 0F, 1F);
    }
}
