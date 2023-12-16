package org.orecruncher.dsurround.lib.math;

import net.minecraft.util.math.Vec3d;
import org.orecruncher.dsurround.lib.random.XorShiftRandom;

import java.util.Random;

public class MathStuff {
    public static final double PHI = 0.5D + Math.sqrt(5) / 2D;  // Golden ratio
    public static final float PHI_F = (float) PHI;
    public static final double ANGLE = PHI * Math.PI * 2D;
    public static final float ANGLE_F = (float) ANGLE;
    public static final float PI_F = (float) Math.PI;
    public static final float E_F = (float) Math.E;

    public static double log(final double value) {
        return value < 0.03D ? Math.log(value) : 6 * (value - 1) / (value + 1 + 4 * (Math.sqrt(value)));
    }

    public static Vec3d normalize(Vec3d vec) {
        double len = Math.sqrt((vec.x * vec.x) + (vec.y * vec.y) * (vec.z * vec.z));
        return new Vec3d(vec.x / len, vec.y / len, vec.z / len);
    }

    public static Vec3d randomPoint(final int minRange, final int maxRange) {
        Random rand = XorShiftRandom.current();

        // Establish a random unit vector
        final double x = rand.nextDouble() - 0.5D;
        final double y = rand.nextDouble() - 0.5D;
        final double z = rand.nextDouble() - 0.5D;
        var vec = new Vec3d(x, y, z).normalize();

        // Establish the range and scaling value
        final int range = maxRange - minRange;
        final double magnitude;

        if (range <= 0) {
            magnitude = minRange;
        } else {
            magnitude = minRange + rand.nextDouble() * range;
        }

        // Generate a vector based on the generated scaling values
        return vec.multiply(magnitude);
    }

    /**
     * Calculate the reflection of a vector based on a surface normal.
     *
     * @param vector        Incoming vector
     * @param surfaceNormal Surface normal
     * @return The reflected vector
     */
    public static Vec3d reflection(final Vec3d vector, final Vec3d surfaceNormal) {
        final double dot2 = vector.dotProduct(surfaceNormal) * 2;
        final double x = vector.x - dot2 * surfaceNormal.x;
        final double y = vector.y - dot2 * surfaceNormal.y;
        final double z = vector.z - dot2 * surfaceNormal.z;
        return new Vec3d(x, y, z);
    }

    /**
     * Simple method to add a scaled addened to a base.  Eliminates unecessary allocations.
     * @param base Base to add another scaled vector to
     * @param addened Vector to scale and add to the base
     * @param scale Scale to apply to the addened vector before adding to the base
     * @return Vector that is a sum of the base and the addened that has been scaled
     */
    public static Vec3d addScaled(final Vec3d base, final Vec3d addened, final double scale) {
        return base.add(addened.getX() * scale, addened.getY() * scale, addened.getZ() * scale);
    }

    public static double pow(final double a, final double b) {
        final long tmp = Double.doubleToRawLongBits(a);
        final long tmp2 = (long) (b * (tmp - 4606921280493453312L)) + 4606921280493453312L;
        return Double.longBitsToDouble(tmp2);
    }

    public static double exp(final double val) {
        final long tmp = (long) (1512775 * val + (1072693248 - 60801));
        return Double.longBitsToDouble(tmp << 32);
    }

    /**
     * Clamps the value between 0 and 1.
     *
     * @param num Number to clamp
     * @return Number clamped between 0 and 1
     */
    public static float clamp1(final float num) {
        return num <= 0 ? 0F : Math.min(num, 1F);
    }

    /**
     * Clamps the value between 0 and 1.
     *
     * @param num Number to clamp
     * @return Number clamped between 0 and 1
     */
    public static double clamp1(final double num) {
        return num <= 0 ? 0F : Math.min(num, 1F);
    }

}
