// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.aurora;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.config.AuroraQuality;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the values the auroras' shaders are given each frame. No GPU needed.
 */
public class AuroraFramesTests {

    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static Matrix4f projection() {
        return new Matrix4f().perspective((float) Math.toRadians(70), 16F / 9F, 0.05F, 512F, true);
    }

    private static RealisticAuroraFrame realistic(RealisticAurora aurora, float alpha, Matrix4f view, int height) {
        var motion = new RealisticAuroraMotion();
        motion.advance(aurora, 1F, 100F);
        return RealisticAuroraFrame.of(aurora, aurora.at(0.5F), motion, AuroraQuality.FANCY, 300F, 0.2F, alpha,
                projection(), view, 100F, height);
    }

    @Test
    void theClassicAurorasStrengthIsItsBrightnessHeldToOne() {
        var aurora = Aurora.create(7, 3);
        var frame = AuroraFrame.of(aurora, 0.5F, 12F);
        assertEquals(Math.clamp(0.5F * aurora.brightness(), 0F, 1F), frame.strength(), 1e-6F);
        assertEquals(12F, frame.time());
        assertSame(aurora.palette(), frame.palette());
        assertTrue(AuroraFrame.of(aurora, 100F, 12F).strength() <= 1F);
    }

    @Test
    void theRealisticAurorasBlockIsFull() {
        var frame = realistic(RealisticAurora.create(11, 10), 0.8F, new Matrix4f(), 1080);
        assertEquals(RealisticAuroraFrame.VECTORS * 4, frame.vec4s().length);
        assertEquals(0.8F, frame.vec4s()[RealisticAuroraFrame.ALPHA], 1e-6F);
        assertEquals(1F, realistic(RealisticAurora.create(11, 10), 3F, new Matrix4f(), 1080).vec4s()[RealisticAuroraFrame.ALPHA]);
    }

    @Test
    void thePixelAngleIsTheScreensHeightOverTheView() {
        var frame = realistic(RealisticAurora.create(11, 10), 1F, new Matrix4f(), 1080);
        assertEquals(2F / (projection().m11() * 1080), frame.vec4s()[RealisticAuroraFrame.PIXEL_ANGLE], 1e-9F);
    }

    @Test
    void theInverseUndoesTheProjectionAndTheView() {
        var view = new Matrix4f().rotateX(-0.6F).rotateY(2F);
        var frame = realistic(RealisticAurora.create(11, 10), 1F, view, 1080);
        var product = new Matrix4f(projection()).mul(view).mul(frame.invViewProj());
        assertTrue(product.equals(new Matrix4f(), 1e-4F), product.toString());
    }

    @Test
    void arcsThereArentDrawNothing() {
        // A night with fewer arcs than there is room for
        RealisticAurora aurora = null;
        for (long night = 0; night < 200 && aurora == null; night++) {
            var candidate = RealisticAurora.create(night, 0);
            if (candidate.arcs().size() < RealisticAurora.MAX_ARCS)
                aurora = candidate;
        }
        assertNotNull(aurora, "a night with room to spare");
        int count = aurora.arcs().size();
        var frame = realistic(aurora, 1F, new Matrix4f(), 1080);
        var v = frame.vec4s();
        assertEquals(count, v[RealisticAuroraFrame.ARC_COUNT]);

        int max = RealisticAurora.MAX_ARCS;
        for (int i = count; i < max; i++) {
            // No brightness, and no wavelength of zero for the shader to divide by
            int brightness = RealisticAuroraFrame.ARC_POSITIONS + (2 * max + i) * 4;
            assertEquals(0F, v[brightness], "arc " + i + "'s brightness");
            assertEquals(1F, v[RealisticAuroraFrame.ARC_POSITIONS + i * 4 + 2], "arc " + i + "'s fold wavelength");
            assertEquals(1F, v[RealisticAuroraFrame.ARC_POSITIONS + (max + i) * 4 + 1], "arc " + i + "'s wave wavelength");
        }
        // The arcs there are come closer as the aurora approaches
        var moment = aurora.at(0.5F);
        assertEquals(aurora.arcs().getFirst().distance() - moment.approach(), v[RealisticAuroraFrame.ARC_POSITIONS], 1e-4F);
    }
}
