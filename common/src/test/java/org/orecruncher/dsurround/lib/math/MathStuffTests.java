package org.orecruncher.dsurround.lib.math;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.lib.random.IRandomizer;

import java.util.ArrayDeque;
import java.util.Deque;

import static org.junit.jupiter.api.Assertions.*;

public class MathStuffTests {

    private static final double EPSILON = 1.0E-9;

    /**
     * Seeded randomizer. Doubles queued with {@link #then} are returned first.
     */
    static final class TestRandomizer implements IRandomizer {
        private final RandomSource source;
        private final Deque<Double> scripted = new ArrayDeque<>();

        TestRandomizer(long seed) {
            this.source = RandomSource.create(seed);
        }

        TestRandomizer then(double... values) {
            for (var v : values)
                this.scripted.add(v);
            return this;
        }

        @Override
        public double nextDouble() {
            return this.scripted.isEmpty() ? this.source.nextDouble() : this.scripted.poll();
        }

        @Override
        public RandomSource fork() {
            return this.source.fork();
        }

        @Override
        public PositionalRandomFactory forkPositional() {
            return this.source.forkPositional();
        }

        @Override
        public void setSeed(long seed) {
            this.source.setSeed(seed);
        }

        @Override
        public int nextInt() {
            return this.source.nextInt();
        }

        @Override
        public int nextInt(int bound) {
            return this.source.nextInt(bound);
        }

        @Override
        public long nextLong() {
            return this.source.nextLong();
        }

        @Override
        public boolean nextBoolean() {
            return this.source.nextBoolean();
        }

        @Override
        public float nextFloat() {
            return this.source.nextFloat();
        }

        @Override
        public double nextGaussian() {
            return this.source.nextGaussian();
        }
    }

    private static void assertVec(Vec3 expected, Vec3 actual) {
        assertEquals(expected.x, actual.x, EPSILON, "x of " + actual);
        assertEquals(expected.y, actual.y, EPSILON, "y of " + actual);
        assertEquals(expected.z, actual.z, EPSILON, "z of " + actual);
    }

    // ---- Vectors ---------------------------------------------------------------------------------------------

    @Test
    void reflectionOffAFloor() {
        // Coming down at 45 degrees, bounces up at 45 degrees
        assertVec(new Vec3(1, 1, 0), MathStuff.reflection(new Vec3(1, -1, 0), new Vec3(0, 1, 0)));
    }

    @Test
    void reflectionOfAHeadOnRayReverses() {
        assertVec(new Vec3(-2, 0, 0), MathStuff.reflection(new Vec3(2, 0, 0), new Vec3(-1, 0, 0)));
    }

    @Test
    void addScaledIsBasePlusScaledAddend() {
        assertVec(new Vec3(11, 22, 33), MathStuff.addScaled(new Vec3(1, 2, 3), new Vec3(5, 10, 15), 2));
    }

    @Test
    void lerpGivesTheMidpointThatAddScaledDoesNot() {
        // Regression: the weather check used addScaled(pt1, pt2, 0.5) as a midpoint
        var a = new Vec3(100, 64, 100);
        var b = new Vec3(110, 64, 100);

        assertVec(new Vec3(105, 64, 100), a.lerp(b, 0.5));
        assertVec(new Vec3(155, 96, 150), MathStuff.addScaled(a, b, 0.5));
    }

    // ---- Clamping and wrapping -------------------------------------------------------------------------------

    @Test
    void clamp1Float() {
        assertEquals(0F, MathStuff.clamp1(-3F));
        assertEquals(0F, MathStuff.clamp1(0F));
        assertEquals(0.25F, MathStuff.clamp1(0.25F));
        assertEquals(1F, MathStuff.clamp1(7F));
    }

    @Test
    void clamp1Double() {
        assertEquals(0D, MathStuff.clamp1(-0.5D));
        assertEquals(0.75D, MathStuff.clamp1(0.75D));
        assertEquals(1D, MathStuff.clamp1(1.5D));
    }

    @Test
    void wrapHandlesNegativeAndLargeValues() {
        assertEquals(0, MathStuff.wrap(0, 5));
        assertEquals(3, MathStuff.wrap(3, 5));
        assertEquals(0, MathStuff.wrap(5, 5));
        assertEquals(2, MathStuff.wrap(12, 5));
        assertEquals(4, MathStuff.wrap(-1, 5));
        assertEquals(3, MathStuff.wrap(-7, 5));
    }

    // ---- Random points ---------------------------------------------------------------------------------------

    @Test
    void randomPointIsWithinRange() {
        var rand = new TestRandomizer(42);
        for (int i = 0; i < 10_000; i++) {
            var length = MathStuff.randomPoint(4, 16, rand).length();
            assertTrue(length >= 4 - EPSILON && length <= 16 + EPSILON, "length " + length);
        }
    }

    @Test
    void emptyRangeUsesTheMinimum() {
        var rand = new TestRandomizer(7);
        assertEquals(8, MathStuff.randomPoint(8, 8, rand).length(), 1.0E-6);
        assertEquals(8, MathStuff.randomPoint(8, 2, rand).length(), 1.0E-6);
    }

    @Test
    void pointNearTheCenterIsRetried() {
        // Regression: a direction sample at (0,0,0) normalized to zero, putting the sound on the player
        var rand = new TestRandomizer(1).then(0.5, 0.5, 0.5);

        var point = MathStuff.randomPoint(10, 10, rand);

        assertEquals(10, point.length(), 1.0E-6);
    }

    @Test
    void pointOutsideTheSphereIsRetried() {
        // (1,1,1) is a cube corner; normalizing it directly is what biased the old directions
        var rand = new TestRandomizer(1).then(1.0, 1.0, 1.0);

        var point = MathStuff.randomPoint(10, 10, rand);

        var direction = point.normalize();
        assertFalse(Math.abs(direction.x - direction.y) < 1.0E-6 && Math.abs(direction.y - direction.z) < 1.0E-6,
                "the corner sample was used: " + direction);
    }

    @Test
    void directionsAreUniform() {
        // With a uniform direction, as many land near the axes as near the cube diagonals (equal solid angles).
        // Normalizing points of a cube gives the diagonals about 1.5 to 2 times as many.
        var rand = new TestRandomizer(12345);
        var cosLimit = Math.cos(Math.toRadians(20));
        double diagonal = 1 / Math.sqrt(3);
        int nearAxis = 0;
        int nearDiagonal = 0;

        for (int i = 0; i < 200_000; i++) {
            var d = MathStuff.randomPoint(1, 1, rand).normalize();
            double ax = Math.abs(d.x), ay = Math.abs(d.y), az = Math.abs(d.z);
            if (Math.max(ax, Math.max(ay, az)) >= cosLimit)
                nearAxis++;
            if ((ax + ay + az) * diagonal >= cosLimit)
                nearDiagonal++;
        }

        // 6 axis directions vs 8 diagonal directions, each a 20 degree cap
        double ratio = (nearDiagonal / 8.0) / (nearAxis / 6.0);
        assertEquals(1.0, ratio, 0.08, "diagonal/axis density ratio");
    }

    @Test
    void goldenAngleSpreadsPointsAroundACircle() {
        // ANGLE is the golden angle plus whole turns: about 137.5 degrees (or 222.5) per step
        var degrees = Math.toDegrees(MathStuff.ANGLE % (2 * Math.PI));
        assertTrue(Math.abs(degrees - 137.5) < 0.1 || Math.abs(degrees - 222.5) < 0.1, "angle " + degrees);
    }
}
