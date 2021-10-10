package org.orecruncher.dsurround.lib.math;

import net.minecraft.client.util.math.Vector3d;
import net.minecraft.util.math.Vec3d;
import org.orecruncher.dsurround.lib.random.XorShiftRandom;

import java.util.Random;

public class MathStuff {
    public static double log(final double value) {
        return value < 0.03D ? Math.log(value) : 6 * (value - 1) / (value + 1 + 4 * (Math.sqrt(value)));
    }

    public static Vector3d normalize(Vector3d vec) {
        double len = Math.sqrt((vec.x * vec.x) + (vec.y * vec.y) * (vec.z * vec.z));
        return new Vector3d(vec.x / len, vec.y / len, vec.z / len);
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
}
