package org.orecruncher.dsurround.effects.particles;

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
    void spinTurnsItAboutTheSurfaceNormal() {
        // The quad's edges stay in the surface: its +X edge is perpendicular to the normal, however it is spun
        var normal = new Vector3f(0.5F, 1F, 0F).normalize();
        for (float spin : new float[]{0F, 1F, 2.5F}) {
            var edge = WaterFoam.orientation(-0.5F, 0F, spin).transform(new Vector3f(1F, 0F, 0F));
            assertEquals(0F, edge.dot(normal), 1.0E-5, "edge " + edge + " at spin " + spin);
        }
    }
}
