package org.orecruncher.dsurround.effects.particles;

import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for how foam lies on the water: the slope it measures from surface heights, and the rotation that lays it on
 * a sloping surface.
 */
public class WaterFoamTests {

    private static final double NONE = Double.NaN;

    // ---- Slope ---------------------------------------------------------------------------------------------------

    @Test
    void slopeAcrossBothSides() {
        // Surface 0.9 one side, 0.7 the other: falling 0.1 per block
        assertEquals(-0.1D, WaterFoam.slope(0.9D, 0.8D, 0.7D), 1.0E-9);
    }

    @Test
    void slopeTowardTheOnlySideWithWater() {
        assertEquals(-0.2D, WaterFoam.slope(NONE, 0.8D, 0.6D), 1.0E-9, "a bank before");
        assertEquals(-0.2D, WaterFoam.slope(1.0D, 0.8D, NONE), 1.0E-9, "a bank after");
    }

    @Test
    void levelBetweenBanks() {
        assertEquals(0D, WaterFoam.slope(NONE, 0.8D, NONE));
    }

    @Test
    void slopeDipsOverAnEdge() {
        // Past the edge the water is a block lower
        assertEquals(-1D, WaterFoam.slope(NONE, 0.8D, -0.2D), 1.0E-9);
    }

    // ---- Orientation ---------------------------------------------------------------------------------------------

    /**
     * The way the quad faces once rotated: its face is +Z before rotating.
     */
    private static Vector3f facing(float slopeX, float slopeZ, float spin) {
        return WaterFoam.orientation(slopeX, slopeZ, spin).transform(new Vector3f(0F, 0F, 1F));
    }

    private static void assertDirection(Vector3f expected, Vector3f actual) {
        assertEquals(expected.x, actual.x, 1.0E-5, "x of " + actual);
        assertEquals(expected.y, actual.y, 1.0E-5, "y of " + actual);
        assertEquals(expected.z, actual.z, 1.0E-5, "z of " + actual);
    }

    @Test
    void levelWaterFacesStraightUpWhateverTheSpin() {
        for (float spin : new float[]{0F, 1F, 2.5F, -0.7F})
            assertDirection(new Vector3f(0F, 1F, 0F), facing(0F, 0F, spin));
    }

    @Test
    void slopingWaterFacesOutOfTheSurfaceWhateverTheSpin() {
        // Falling toward +X (east) by 0.5 per block: the surface faces up and a little east
        var normal = new Vector3f(0.5F, 1F, 0F).normalize();
        for (float spin : new float[]{0F, 1F, 2.5F})
            assertDirection(normal, facing(-0.5F, 0F, spin));

        // Rising toward +Z (south): it faces up and a little north
        assertDirection(new Vector3f(0F, 1F, -0.3F).normalize(), facing(0F, 0.3F, 0F));
    }

    @Test
    void aRotationSetAgainIsTheSameAsANewOne() {
        // Each patch keeps its rotation and sets it again every frame: what it held before mustn't carry over
        var kept = WaterFoam.orientation(0.7F, -0.4F, 2F);
        for (float[] s : new float[][]{{0F, 0F, 0F}, {-0.5F, 0.3F, 1F}, {1.2F, 0F, -2.5F}}) {
            var fresh = WaterFoam.orientation(s[0], s[1], s[2]);
            var reused = WaterFoam.orientation(s[0], s[1], s[2], kept);
            assertSame(kept, reused);
            assertDirection(fresh.transform(new Vector3f(0F, 0F, 1F)), reused.transform(new Vector3f(0F, 0F, 1F)));
            assertDirection(fresh.transform(new Vector3f(1F, 0F, 0F)), reused.transform(new Vector3f(1F, 0F, 0F)));
        }
    }

    /**
     * The angle between where two rotations take the quad's face and its +X edge, the larger, in degrees
     */
    private static double degreesApart(Quaternionf a, Quaternionf b) {
        double worst = 0;
        for (var axis : new Vector3f[]{new Vector3f(0F, 0F, 1F), new Vector3f(1F, 0F, 0F)}) {
            var u = a.transform(new Vector3f(axis));
            var v = b.transform(new Vector3f(axis));
            worst = Math.max(worst, Math.toDegrees(u.angle(v)));
        }
        return worst;
    }

    @Test
    void aBlendBetweenTicksFollowsTheWorkedOutOrientation() {
        // As foam turns in a tick: its spin by up to 0.03 rad, its slope eased 0.3 of the way to the surface's, up to
        // the steepest (tan 60 degrees) from level, the largest turn it makes, landing on a step's edge
        float steepest = (float) Math.tan(Math.toRadians(60D));
        float[][] ticks = {
                // slope x, z before; after; spin before; after
                {0F, 0F, 0.3F * steepest, 0F, 1F, 1.03F},
                {0F, 0F, 0F, -0.3F * steepest, -2F, -2.03F},
                {0.2F, -0.1F, 0.25F, -0.12F, 0.5F, 0.53F},
                {-1F, 0.5F, -1.05F, 0.52F, 3F, 2.97F},
        };
        double worst = 0;
        for (var t : ticks) {
            var from = WaterFoam.orientation(t[0], t[1], t[4]);
            var to = WaterFoam.orientation(t[2], t[3], t[5]);
            for (float partial = 0F; partial <= 1F; partial += 0.125F) {
                var blended = WaterFoam.blend(from, to, partial, new Quaternionf());
                var exact = WaterFoam.orientation(Mth.lerp(partial, t[0], t[2]), Mth.lerp(partial, t[1], t[3]),
                        Mth.lerp(partial, t[4], t[5]));
                worst = Math.max(worst, degreesApart(blended, exact));
            }
        }
        // Most is in the landing tick, the 27 degree tilt (0.84 degrees at most, half way through the tick); neither
        // way between ticks is the true one, and a patch is a third of a block across
        assertTrue(worst < 1D, "blend strays " + worst + " degrees from the worked out orientation");
    }

    @Test
    void aBlendStartsAndEndsOnItsTicks() {
        var from = WaterFoam.orientation(0.2F, -0.4F, 1F);
        var to = WaterFoam.orientation(0.3F, -0.35F, 1.03F);
        assertEquals(0D, degreesApart(from, WaterFoam.blend(from, to, 0F, new Quaternionf())), 1.0E-3);
        assertEquals(0D, degreesApart(to, WaterFoam.blend(from, to, 1F, new Quaternionf())), 1.0E-3);
    }

    @Test
    void aBlendTakesTheShortWayRound() {
        // q and -q are the same rotation: a blend between them stays put rather than swinging the long way round
        var q = WaterFoam.orientation(0.4F, 0.1F, 2F);
        var negated = new Quaternionf(-q.x, -q.y, -q.z, -q.w);
        for (float partial = 0F; partial <= 1F; partial += 0.25F)
            assertEquals(0D, degreesApart(q, WaterFoam.blend(q, negated, partial, new Quaternionf())), 1.0E-3);
    }

    @Test
    void spinTurnsItAboutTheSurfaceNormal() {
        // The quad's edges stay in the surface: its +X edge is perpendicular to the normal, however it is spun
        var normal = new Vector3f(0.5F, 1F, 0F).normalize();
        for (float spin : new float[]{0F, 1F, 2.5F}) {
            var edge = WaterFoam.orientation(-0.5F, 0F, spin).transform(new Vector3f(1F, 0F, 0F));
            assertEquals(0F, edge.dot(normal), 1.0E-5, "edge " + edge + " at spin " + spin);
        }
    }

    @Test
    void aSurfaceIsSharedForAWhileThenWorkedOutAgain() {
        long then = 1000L;
        assertFalse(WaterFoam.isStale(then, then));
        assertFalse(WaterFoam.isStale(then, then + WaterFoam.SURFACE_TICKS - 1));
        assertTrue(WaterFoam.isStale(then, then + WaterFoam.SURFACE_TICKS));
        // The clock went back: nothing from before can be trusted
        assertTrue(WaterFoam.isStale(then, then - 1));
    }

    @Test
    void waterChangesAreFollowedWithinHalfASecond() {
        assertTrue(WaterFoam.SURFACE_TICKS <= 10);
        // And a patch's light is read at least every half second too
        assertTrue(WaterFoam.LIGHT_TICKS <= 10);
    }
}
