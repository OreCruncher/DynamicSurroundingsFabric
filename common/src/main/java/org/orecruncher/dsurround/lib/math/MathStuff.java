package org.orecruncher.dsurround.lib.math;

import net.minecraft.world.phys.Vec3;
import org.orecruncher.dsurround.lib.random.IRandomizer;
import org.orecruncher.dsurround.lib.random.Randomizer;

public final class MathStuff {

    private static final double PHI = 0.5D + Math.sqrt(5) / 2D;  // Golden ratio

    /**
     * The golden angle (in radians, plus whole turns): stepping around a circle by this much spreads points evenly.
     */
    public static final double ANGLE = PHI * Math.PI * 2D;

    private MathStuff() {
    }

    /**
     * A random point between {@code minRange} and {@code maxRange} blocks from the origin, in a uniformly random
     * direction. If the range is empty, the point is {@code minRange} away.
     */
    public static Vec3 randomPoint(final int minRange, final int maxRange) {
        return randomPoint(minRange, maxRange, Randomizer.current());
    }

    static Vec3 randomPoint(final int minRange, final int maxRange, final IRandomizer rand) {
        // A uniformly random direction: a random point in the unit cube, kept only if it is inside the unit sphere
        // (and not too close to the center to normalize). Normalizing any point of the cube would favor the
        // cube's diagonals.
        double x, y, z, lengthSq;
        do {
            x = rand.nextDouble() * 2D - 1D;
            y = rand.nextDouble() * 2D - 1D;
            z = rand.nextDouble() * 2D - 1D;
            lengthSq = x * x + y * y + z * z;
        } while (lengthSq > 1D || lengthSq < 1.0E-4D);

        final int range = maxRange - minRange;
        final double magnitude = range <= 0 ? minRange : minRange + rand.nextDouble() * range;

        final double scale = magnitude / Math.sqrt(lengthSq);
        return new Vec3(x * scale, y * scale, z * scale);
    }

    /**
     * Calculate the reflection of a vector based on a surface normal.
     *
     * @param vector        Incoming vector
     * @param surfaceNormal Surface normal (unit length)
     * @return The reflected vector
     */
    public static Vec3 reflection(final Vec3 vector, final Vec3 surfaceNormal) {
        final double dot2 = vector.dot(surfaceNormal) * 2;
        final double x = vector.x - dot2 * surfaceNormal.x;
        final double y = vector.y - dot2 * surfaceNormal.y;
        final double z = vector.z - dot2 * surfaceNormal.z;
        return new Vec3(x, y, z);
    }

    /**
     * {@code base + addend * scale}, without the intermediate vector {@code addend * scale}. Not a midpoint: for
     * the point halfway between two positions use {@code a.lerp(b, 0.5)}.
     *
     * @param base   Base to add another scaled vector to
     * @param addend Vector to scale and add to the base
     * @param scale  Scale to apply to the addend before adding it to the base
     * @return The sum of the base and the scaled addend
     */
    public static Vec3 addScaled(final Vec3 base, final Vec3 addend, final double scale) {
        return base.add(addend.x() * scale, addend.y() * scale, addend.z() * scale);
    }

    /**
     * Clamps the value between 0 and 1.
     *
     * @param num Number to clamp
     * @return Number clamped between 0 and 1
     */
    public static float clamp1(final float num) {
        return num <= 0F ? 0F : Math.min(num, 1F);
    }

    /**
     * Clamps the value between 0 and 1.
     *
     * @param num Number to clamp
     * @return Number clamped between 0 and 1
     */
    public static double clamp1(final double num) {
        return num <= 0D ? 0D : Math.min(num, 1D);
    }

    /**
     * Wraps the value into the range 0 (inclusive) to size (exclusive), including negative values: -1 wraps to
     * size - 1.
     */
    public static int wrap(int value, int size) {
        return Math.floorMod(value, size);
    }
}
