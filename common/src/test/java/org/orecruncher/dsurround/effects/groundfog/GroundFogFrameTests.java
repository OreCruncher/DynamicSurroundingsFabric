// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.groundfog;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.config.GroundFogQuality;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the values the ground fog's passes are given each frame, and how its grid is laid out in the texture.
 * Plain Java: no GPU needed.
 */
public class GroundFogFrameTests {

    private static final int GROUND = 64;
    private static final int LIGHT = (11 << 4) | (13 << 20);

    /**
     * Flat ground at {@link #GROUND}, foggy west of {@code fogBelowX}
     */
    private record Ground(int fogBelowX) implements IGroundFogSource {
        @Override
        public int groundHeight(int x, int z) {
            return GROUND;
        }

        @Override
        public float density(int x, int y, int z) {
            return x < this.fogBelowX ? 0.8F : 0F;
        }

        @Override
        public int light(int x, int y, int z) {
            return LIGHT;
        }
    }

    private static GroundFogField field(int fogBelowX, double camX, double camZ) {
        var field = new GroundFogField(8);
        field.update(new Ground(fogBelowX), camX, camZ, Float.NaN);
        return field;
    }

    private static Matrix4f projection() {
        return new Matrix4f().perspective((float) Math.toRadians(70), 16F / 9F, 0.05F, 512F, true);
    }

    private static GroundFogFrame frame(GroundFogField field, double camX, double camY, double camZ, int baseY, float highest,
                                        Matrix4f view, float strength, boolean zeroToOne) {
        return GroundFogFrame.of(field, camX, camY, camZ, baseY, highest, projection(), view, 1920, 1080,
                GroundFogQuality.FANCY, strength, 3F, -2F, 12.5F, zeroToOne);
    }

    @Test
    void theInverseUndoesTheProjectionAndTheView() {
        var view = new Matrix4f().rotateX(0.3F).rotateY(1.1F);
        var field = field(Integer.MAX_VALUE, 0.5, 0.5);
        var frame = frame(field, 0.5, 70, 0.5, 60, GROUND, view, 1F, true);

        var product = new Matrix4f(projection()).mul(view).mul(frame.invViewProj());
        assertTrue(product.equals(new Matrix4f(), 1e-4F), product.toString());
    }

    @Test
    void theCameraIsGivenInTheGridsTerms() {
        var field = field(Integer.MAX_VALUE, 100.5, -40.25);
        int baseY = 60;
        var frame = frame(field, 100.5, 70.75, -40.25, baseY, GROUND, new Matrix4f(), 1F, true);
        var v = frame.vec4s();

        int c = GroundFogFrame.CAMERA;
        assertEquals(100.5F - field.cellX(0), v[c], 1e-4F);
        assertEquals(70.75F - baseY, v[c + 1], 1e-4F);
        assertEquals(-40.25F - field.cellZ(0), v[c + 2], 1e-4F);
        assertEquals(baseY, v[c + 3]);
        // The camera is at the middle of the grid
        assertEquals(field.radius(), v[c], 1F);
    }

    @Test
    void theNoiseOriginWrapsFarOut() {
        double far = 3_000_000.5;
        var field = field(Integer.MAX_VALUE, far, -far);
        var frame = frame(field, far, 70, -far, 60, GROUND, new Matrix4f(), 1F, true);
        var v = frame.vec4s();

        int n = GroundFogFrame.NOISE_ORIGIN;
        assertEquals(Math.floorMod(field.cellX(0), GroundFogVolume.NOISE_WRAP), v[n], 0F);
        assertEquals(Math.floorMod(field.cellZ(0), GroundFogVolume.NOISE_WRAP), v[n + 1], 0F);
        assertTrue(v[n] >= 0F && v[n] < GroundFogVolume.NOISE_WRAP);
        assertTrue(v[n + 1] >= 0F && v[n + 1] < GroundFogVolume.NOISE_WRAP);
    }

    @Test
    void theMistsTopThicknessAndEdgeFollowTheField() {
        var field = field(Integer.MAX_VALUE, 0.5, 0.5);
        int baseY = 60;
        float highest = 66.5F;
        var thin = frame(field, 0.5, 70, 0.5, baseY, highest, new Matrix4f(), 0.25F, false).vec4s();

        assertEquals(highest + GroundFogVolume.MIST_DEPTH - baseY, thin[GroundFogFrame.TOP], 1e-5F);
        assertEquals(GroundFogVolume.THICKNESS * 0.25F, thin[GroundFogFrame.THICKNESS], 1e-6F);
        assertEquals(field.radius() - 1F, thin[GroundFogFrame.FADE]);
        assertEquals(field.radius() * GroundFogField.EDGE_FADE, thin[GroundFogFrame.FADE + 1], 1e-6F);
        assertEquals(0F, thin[GroundFogFrame.THICKNESS + 1], "depth from -1 to 1");
        assertEquals(1F, frame(field, 0.5, 70, 0.5, baseY, highest, new Matrix4f(), 1F, true).vec4s()[GroundFogFrame.THICKNESS + 1]);
    }

    @Test
    void aTexelHoldsTheHeightTheMistAndTheLight() {
        var field = field(Integer.MAX_VALUE, 0.5, 0.5);
        int baseY = GROUND - 3;
        int texel = GroundFogFrame.texel(field, field.radius(), field.radius(), baseY);

        float above = field.smoothHeight(field.radius(), field.radius()) - baseY;
        assertEquals((int) above, texel & 0xFF, "whole blocks above the base");
        assertEquals(Math.round((above - (int) above) * 255F), (texel >>> 24) & 0xFF, "the part of a block over");
        assertEquals(Math.round(0.8F * 255F), (texel >> 8) & 0xFF, "the mist");
        assertEquals((11 << 4) | 13, (texel >> 16) & 0xFF, "block light, then sky light");
    }

    @Test
    void theGroundBelowTheBaseIsHeldAtIt() {
        var field = field(Integer.MAX_VALUE, 0.5, 0.5);
        int texel = GroundFogFrame.texel(field, 0, 0, GROUND + 10);
        assertEquals(0, texel & 0xFF);
        assertEquals(0, (texel >>> 24) & 0xFF);
    }

    @Test
    void theLowestAndHighestGroundAreFound() {
        var foggy = field(Integer.MAX_VALUE, 0.5, 0.5);
        assertEquals(GROUND, GroundFogFrame.lowestGround(foggy), 1e-5F);
        assertEquals(GROUND, GroundFogFrame.highestUnderMist(foggy), 1e-5F);

        var clear = field(Integer.MIN_VALUE, 0.5, 0.5);
        assertEquals(Float.NEGATIVE_INFINITY, GroundFogFrame.highestUnderMist(clear));
    }
}
