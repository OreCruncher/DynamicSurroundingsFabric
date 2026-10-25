package org.orecruncher.dsurround.effects.clouds;

/**
 * Light inside the clouds for one frame: up to {@link #MAX} points, each with a reach and a power, which the cloud
 * shader adds to the clouds around them (lightning's glow, lighting the deck from inside rather than drawn as patches of
 * its own). Positions are from the camera. When more are offered than fit, the strongest are kept. Render thread only.
 */
public final class CloudLights {

    /**
     * The most lights a frame: the shader has a uniform for each
     */
    public static final int MAX = 8;
    // Less than this adds nothing worth the shader's time
    static final float MIN_POWER = 0.01F;

    // x, y, z and reach of each; and its power
    private final float[] points = new float[MAX * 4];
    private final float[] powers = new float[MAX];
    private int count;

    /**
     * Something that lights the clouds: offers its lights for a frame
     */
    @FunctionalInterface
    public interface Source {
        /**
         * @param partialTick the part of a tick into the next
         * @param cameraX     where the camera is, for positions from it; and {@code cameraY}, {@code cameraZ}
         */
        void offer(CloudLights lights, float partialTick, double cameraX, double cameraY, double cameraZ);
    }

    public void clear() {
        this.count = 0;
    }

    /**
     * Offers a light: kept if there is room, or if it is stronger than the weakest kept, which it takes the place of
     *
     * @param reach how far its light goes, in blocks
     * @param power how bright it is where it is, 0 to about 1.5
     */
    public void add(float x, float y, float z, float reach, float power) {
        if (power < MIN_POWER || reach <= 0F)
            return;
        int slot = this.count;
        if (slot >= MAX) {
            slot = 0;
            for (int i = 1; i < MAX; i++)
                if (this.powers[i] < this.powers[slot])
                    slot = i;
            if (this.powers[slot] >= power)
                return;
        } else {
            this.count++;
        }
        this.points[slot * 4] = x;
        this.points[slot * 4 + 1] = y;
        this.points[slot * 4 + 2] = z;
        this.points[slot * 4 + 3] = reach;
        this.powers[slot] = power;
    }

    public int count() {
        return this.count;
    }

    public float x(int light) {
        return this.points[light * 4];
    }

    public float y(int light) {
        return this.points[light * 4 + 1];
    }

    public float z(int light) {
        return this.points[light * 4 + 2];
    }

    public float reach(int light) {
        return this.points[light * 4 + 3];
    }

    /**
     * A light's power; 0 for one not in use
     */
    public float power(int light) {
        return light < this.count ? this.powers[light] : 0F;
    }
}
