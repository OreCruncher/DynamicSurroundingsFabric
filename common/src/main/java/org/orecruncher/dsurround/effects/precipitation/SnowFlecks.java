package org.orecruncher.dsurround.effects.precipitation;

import com.mojang.blaze3d.vertex.Tesselator;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import java.util.List;

/**
 * Where snowflakes landed: each lies flat where it came down, as the flake it fell as, for a moment, then shrinks and
 * fades as it melts into the ground. One blown into the side of something sticks to it, flat on the face it hit. The snow's counterpart of {@link DampSpots}, kept in flat arrays the same way and
 * drawn with the falling snow; flecks added when it is full are left out. Client thread only, which in Minecraft is
 * also the render thread.
 */
public final class SnowFlecks {

    /**
     * Which face of a block a fleck lies on: the top, or a side, named by the way the face looks (west looks toward
     * negative x)
     */
    public static final byte FACE_TOP = 0;
    public static final byte FACE_WEST = 1;
    public static final byte FACE_EAST = 2;
    public static final byte FACE_NORTH = 3;
    public static final byte FACE_SOUTH = 4;

    // Ticks a fleck lasts: at least this, then a random number more
    static final int MIN_LIFETIME = 30;
    static final int LIFETIME_RANGE = 40;
    // The share of its life it lies there whole; for the rest it melts: fading, and shrinking to MELTED_SIZE of itself
    static final float WHOLE_SHARE = 0.4F;
    static final float MELTED_SIZE = 0.5F;
    // Just off the surface, and a little apart from each other, so neither the surface nor other flecks show through
    static final float LIFT = 0.006F;
    static final float LIFT_RANGE = 0.004F;

    private final int capacity;
    final float[] x, y, z;
    // The cos and sin of the angle it lies at
    final float[] turnCos, turnSin;
    // How big the flake was, 0 to 1, and which flake it was drawn as, 0 to 1 (see PrecipitationField.variant)
    final float[] size, variant;
    final int[] age, lifetime, light;
    // The face it lies on (see FACE_TOP)
    final byte[] face;
    int count;

    public SnowFlecks(int capacity) {
        this.capacity = capacity;
        this.x = new float[capacity];
        this.y = new float[capacity];
        this.z = new float[capacity];
        this.turnCos = new float[capacity];
        this.turnSin = new float[capacity];
        this.size = new float[capacity];
        this.variant = new float[capacity];
        this.age = new int[capacity];
        this.lifetime = new int[capacity];
        this.light = new int[capacity];
        this.face = new byte[capacity];
    }

    public int count() {
        return this.count;
    }

    public void clear() {
        this.count = 0;
    }

    /**
     * Adds a fleck where a snowflake landed on top of something, at a random angle
     *
     * @param surfaceY the height of the surface it landed on
     * @param size     how big the flake was, 0 to 1
     * @param variant  which flake it was drawn as, 0 to 1
     * @param light    its packed light
     */
    public void add(double x, double surfaceY, double z, float size, float variant, int light, RandomSource random) {
        this.add(x, surfaceY, z, FACE_TOP, size, variant, light, random);
    }

    /**
     * Adds a fleck where a snowflake landed on a face of a block, at a random angle
     *
     * @param x    where on the face: for the top, its height is {@code y}; for a side, the side is at {@code x} (west
     *             or east) or {@code z} (north or south)
     * @param face the face (see {@link #FACE_TOP})
     */
    public void add(double x, double y, double z, byte face, float size, float variant, int light, RandomSource random) {
        if (this.count == this.capacity)
            return;
        int i = this.count++;
        float angle = random.nextFloat() * Mth.TWO_PI;
        float lift = LIFT + random.nextFloat() * LIFT_RANGE;
        this.x[i] = (float) x + normalX(face) * lift;
        this.y[i] = (float) y + (face == FACE_TOP ? lift : 0F);
        this.z[i] = (float) z + normalZ(face) * lift;
        this.face[i] = face;
        this.turnCos[i] = Mth.cos(angle);
        this.turnSin[i] = Mth.sin(angle);
        this.size[i] = size;
        this.variant[i] = variant;
        this.age[i] = 0;
        this.lifetime[i] = MIN_LIFETIME + random.nextInt(LIFETIME_RANGE);
        this.light[i] = light;
    }

    /**
     * Ages the flecks, dropping those that have melted
     */
    public void tick() {
        int i = 0;
        while (i < this.count) {
            if (++this.age[i] >= this.lifetime[i]) {
                int last = --this.count;
                this.x[i] = this.x[last];
                this.y[i] = this.y[last];
                this.z[i] = this.z[last];
                this.turnCos[i] = this.turnCos[last];
                this.turnSin[i] = this.turnSin[last];
                this.size[i] = this.size[last];
                this.variant[i] = this.variant[last];
                this.age[i] = this.age[last];
                this.lifetime[i] = this.lifetime[last];
                this.light[i] = this.light[last];
                this.face[i] = this.face[last];
            } else {
                i++;
            }
        }
    }

    /**
     * Draws the flecks with the current shader, which should be {@link PrecipitationShaders#DROPS}, and the snow
     * texture bound, as for the falling snow. Render thread only.
     *
     * @param snowflakes the flakes of the snow texture; empty to draw plain flecks
     * @param largest    the largest of them, in pixels: the others are drawn in proportion
     * @param scale      how big snowflakes are drawn
     */
    public void draw(Tesselator tesselator, Camera camera, float partialTick, List<Snowflakes.Flake> snowflakes, int largest, float scale) {
        PrecipitationRenderer.draw(this, tesselator, camera, partialTick, snowflakes, largest, scale);
    }

    /**
     * Which way a face looks along x: -1 for west, 1 for east, otherwise 0
     */
    static int normalX(byte face) {
        return face == FACE_WEST ? -1 : face == FACE_EAST ? 1 : 0;
    }

    /**
     * Which way a face looks along z: -1 for north, 1 for south, otherwise 0
     */
    static int normalZ(byte face) {
        return face == FACE_NORTH ? -1 : face == FACE_SOUTH ? 1 : 0;
    }

    /**
     * How much of a fleck is left, 0 to 1: all of it at first, then less as it melts, none once it has
     */
    float remaining(int i, float partialTick) {
        return remainingForAge(this.age[i] + partialTick, this.lifetime[i]);
    }

    static float remainingForAge(float age, int lifetime) {
        if (age >= lifetime)
            return 0F;
        float melting = (age / lifetime - WHOLE_SHARE) / (1F - WHOLE_SHARE);
        return 1F - Mth.clamp(melting, 0F, 1F);
    }

    /**
     * How big a fleck is drawn, as a share of the flake it fell as, for how much of it is left
     */
    static float sizeFor(float remaining) {
        return Mth.lerp(remaining, MELTED_SIZE, 1F);
    }
}
