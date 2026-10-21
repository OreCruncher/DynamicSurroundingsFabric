package org.orecruncher.dsurround.effects.precipitation;

import com.mojang.blaze3d.vertex.Tesselator;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * Where rain drops soaked into soft ground (grass, dirt, sand, wool): spots lying flat on it that darken at once and
 * fade as they dry. Overlapping spots darken the ground further, so heavier rain leaves it looking wetter. Kept in
 * flat arrays like the drops, and drawn with them; spots added when the field is full are left out. Client thread
 * only, which in Minecraft is also the render thread.
 */
public final class DampSpots {

    /**
     * How dark the spot of the largest drop gets; the smallest drop's is {@link #SMALL_DROP_DARKNESS} of that
     */
    static final float PEAK_DARKNESS = 0.35F;
    static final float SMALL_DROP_DARKNESS = 0.4F;
    // Ticks to darken fully; the rest of its life it dries
    static final int DARKEN_TICKS = 2;
    // Ticks to dry: the smallest drop's spot, and how much longer the largest's takes; then a random number more
    static final int MIN_LIFETIME = 40;
    static final int LIFETIME_PER_SIZE = 40;
    static final int LIFETIME_RANGE = 30;
    // Half the side of the square: the smallest drop's spot, and how much bigger the largest's is; then up to a
    // fraction bigger or smaller at random
    static final float MIN_HALF_SIZE = 0.06F;
    static final float HALF_SIZE_PER_SIZE = 0.16F;
    static final float HALF_SIZE_VARIATION = 0.2F;
    // Just above the surface, and a little apart from each other, so neither the surface nor other spots show through
    static final float LIFT = 0.005F;
    static final float LIFT_RANGE = 0.004F;

    private final int capacity;
    final float[] x, y, z;
    // Half the side of the square, turned by the spot's angle: the corners are the centre plus and minus these
    final float[] cornerX, cornerZ;
    final int[] age, lifetime;
    // How dark the spot gets, 0 to 1
    final float[] peak;
    int count;

    public DampSpots(int capacity) {
        this.capacity = capacity;
        this.x = new float[capacity];
        this.y = new float[capacity];
        this.z = new float[capacity];
        this.cornerX = new float[capacity];
        this.peak = new float[capacity];
        this.cornerZ = new float[capacity];
        this.age = new int[capacity];
        this.lifetime = new int[capacity];
    }

    public int count() {
        return this.count;
    }

    public void clear() {
        this.count = 0;
    }

    /**
     * Adds a spot, turned at random so the spots don't all look alike. A bigger drop leaves a bigger, darker spot that
     * takes longer to dry.
     *
     * @param surfaceY the height of the surface the drop soaked into
     * @param dropSize how big the drop was, 0 to 1
     */
    public void add(double x, double surfaceY, double z, float dropSize, RandomSource random) {
        if (this.count == this.capacity)
            return;
        int i = this.count++;
        float variation = 1F + (random.nextFloat() * 2F - 1F) * HALF_SIZE_VARIATION;
        float half = (MIN_HALF_SIZE + HALF_SIZE_PER_SIZE * dropSize) * variation;
        float angle = random.nextFloat() * Mth.HALF_PI;
        this.x[i] = (float) x;
        this.y[i] = (float) surfaceY + LIFT + random.nextFloat() * LIFT_RANGE;
        this.z[i] = (float) z;
        this.cornerX[i] = Mth.cos(angle) * half;
        this.cornerZ[i] = Mth.sin(angle) * half;
        this.age[i] = 0;
        this.lifetime[i] = MIN_LIFETIME + (int) (LIFETIME_PER_SIZE * dropSize) + random.nextInt(LIFETIME_RANGE);
        this.peak[i] = SMALL_DROP_DARKNESS + (1F - SMALL_DROP_DARKNESS) * dropSize;
    }

    /**
     * Ages the spots, dropping those that have dried
     */
    public void tick() {
        int i = 0;
        while (i < this.count) {
            if (++this.age[i] >= this.lifetime[i]) {
                int last = --this.count;
                this.x[i] = this.x[last];
                this.y[i] = this.y[last];
                this.z[i] = this.z[last];
                this.cornerX[i] = this.cornerX[last];
                this.cornerZ[i] = this.cornerZ[last];
                this.age[i] = this.age[last];
                this.lifetime[i] = this.lifetime[last];
                this.peak[i] = this.peak[last];
            } else {
                i++;
            }
        }
    }

    /**
     * Draws the spots with the current shader, which should be {@link PrecipitationShaders#DAMP_SPOTS}, and the
     * particle atlas bound. Render thread only.
     *
     * @param darkening true when the blend darkens the ground by each spot's alpha; false to lay a dark colour over
     *                  it with an ordinary blend
     */
    public void draw(Tesselator tesselator, Camera camera, float partialTick, TextureAtlasSprite sprite, boolean darkening) {
        PrecipitationRenderer.draw(this, tesselator, camera, partialTick, sprite, darkening);
    }

    /**
     * How much a spot darkens the ground, 0 to 1
     */
    float darkness(int i, float partialTick) {
        return this.peak[i] * darknessForAge(this.age[i] + partialTick, this.lifetime[i]);
    }

    /**
     * Darkens quickly, then fades as it dries, slowing toward the end. For the largest drop's spot; others are scaled
     * down from it.
     */
    static float darknessForAge(float age, int lifetime) {
        if (age >= lifetime)
            return 0F;
        var darken = Math.min(1F, age / DARKEN_TICKS);
        var remaining = 1F - age / lifetime;
        return PEAK_DARKNESS * darken * remaining * remaining;
    }
}
