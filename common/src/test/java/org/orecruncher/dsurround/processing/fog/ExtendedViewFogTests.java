// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.processing.fog;

import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.util.Mth;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.lib.compat.FogCompat;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for our fog added after a mod that draws terrain past the render distance has stretched the game's.
 */
public class ExtendedViewFogTests {

    // Render distance 16, distant terrain out to 128 chunks
    private static final float RENDER_DISTANCE = 256F;
    private static final float VIEW = RENDER_DISTANCE + 128 * 16F;
    // The game's clear weather haze
    private static final float GAME_END = 1024F;

    private static FogData fog(float start, float end, float renderDistanceEnd) {
        var data = new FogData();
        data.environmentalStart = start;
        data.environmentalEnd = end;
        data.renderDistanceStart = renderDistanceEnd - 16F;
        data.renderDistanceEnd = renderDistanceEnd;
        data.skyEnd = end;
        data.cloudEnd = end;
        return data;
    }

    /**
     * What a distant terrain mod tried does to the environmental fog at the return of FogRenderer.setupFog: multiplied by the view
     * over the render distance while it ends at least 0.9 of the render distance away, otherwise left alone
     */
    private static FogData stretchedByOtherMod(FogData data) {
        var result = FogCompat.copy(data);
        result.renderDistanceEnd = VIEW;
        if (data.environmentalEnd >= RENDER_DISTANCE * 0.9F) {
            float k = VIEW / RENDER_DISTANCE;
            result.environmentalStart *= k;
            result.environmentalEnd *= k;
        }
        return result;
    }

    // Our fog with a dust storm's thickening of the given strength, from the game's clear weather haze
    private static FogData dust(FogData given, float strength) {
        return VanillaFogRangeCalculator.thicken(FogCompat.copy(given), RENDER_DISTANCE,
                strength * WeatherFogRangeCalculator.DUST_FOG_INTENSITY, WeatherFogRangeCalculator.DUST_MIN_START);
    }

    // The frame's fog with a dust storm of the given strength, ours held back and put in after the other mod
    private static FogData finished(float strength) {
        var given = fog(0F, GAME_END, RENDER_DISTANCE);
        var target = stretchedByOtherMod(given);
        ExtendedViewFog.apply(target, given, dust(given, strength), RENDER_DISTANCE);
        return target;
    }

    // How much of a linear fog lies over something this far away, 0 to 1
    private static float fogAt(FogData data, float distance) {
        return Mth.clamp((distance - data.environmentalStart) / (data.environmentalEnd - data.environmentalStart), 0F, 1F);
    }

    @Test
    void theViewReachesPastTheRenderDistanceOnlyWhenItsFogEndsPastIt() {
        assertFalse(ExtendedViewFog.isExtended(fog(0F, GAME_END, RENDER_DISTANCE), RENDER_DISTANCE), "vanilla ends it at the render distance");
        assertTrue(ExtendedViewFog.isExtended(fog(0F, GAME_END, VIEW), RENDER_DISTANCE));
        assertFalse(ExtendedViewFog.isExtended(fog(0F, GAME_END, 64F), RENDER_DISTANCE), "brought nearer is not past it");
    }

    @Test
    void aDustStormClosesInWithoutAJump() {
        // Regression: as a dust storm built, the view snapped from about 2 km to about 230 blocks at 25% (in game,
        // 2026-11-08), where our fog crossed the line the other mod stops stretching at. Now each step closes in a little
        var previous = finished(0F);
        for (int i = 1; i <= 100; i++) {
            var now = finished(i / 100F);
            assertTrue(now.environmentalEnd <= previous.environmentalEnd + 0.01F, "never thins as the storm builds, at " + i + "%");
            for (float distance : new float[]{RENDER_DISTANCE, 1000F, VIEW})
                // The steepest is about 0.13, around 28% at 1000 blocks (see ExtendedViewFog.stretch)
                assertTrue(fogAt(now, distance) - fogAt(previous, distance) < 0.15F,
                        "no jump at " + i + "% for " + distance + " blocks: " + previous.environmentalEnd + " to " + now.environmentalEnd);
            previous = now;
        }
    }

    @Test
    void theHorizonFadesAsTheRenderDistancesEdgeDidWithoutIt() {
        // While our fog is light, the other mod's horizon hazes over about as much as the render distance's edge does
        // without it (our start isn't stretched, so up to 0.09 more, at 16%)
        var given = fog(0F, GAME_END, RENDER_DISTANCE);
        for (int i = 0; i <= 20; i += 2) {
            float without = fogAt(dust(given, i / 100F), RENDER_DISTANCE);
            assertEquals(without, fogAt(finished(i / 100F), VIEW), 0.1F, "at " + i + "%");
        }
    }

    @Test
    void noFogOfOursLeavesTheStretchedFogAsItIs() {
        assertEquals(VIEW / RENDER_DISTANCE * GAME_END, finished(0F).environmentalEnd, 0.5F);
    }

    @Test
    void aThickFogOfOursIsBarelyChanged() {
        var given = fog(0F, GAME_END, RENDER_DISTANCE);
        var ours = VanillaFogRangeCalculator.thicken(FogCompat.copy(given), RENDER_DISTANCE, 0.85F, 2F);
        float end = finished(1F).environmentalEnd;
        assertTrue(end >= ours.environmentalEnd, "never thicker than ours");
        assertEquals(ours.environmentalEnd, end, ours.environmentalEnd * 0.05F, "a full dust storm stays as thick");
    }

    @Test
    void withoutStretchingOursIsAppliedAsItIs() {
        var given = fog(5F, 300F, RENDER_DISTANCE);
        var ours = fog(2F, 100F, RENDER_DISTANCE);
        ours.skyEnd = 150F;
        ours.cloudEnd = 120F;
        var target = FogCompat.copy(given);
        ExtendedViewFog.apply(target, given, ours, RENDER_DISTANCE);
        assertEquals(2F, target.environmentalStart);
        assertEquals(100F, target.environmentalEnd);
        assertEquals(150F, target.skyEnd);
        assertEquals(120F, target.cloudEnd);
    }

    @Test
    void theSkyLosesTheHazeTheTerrainDid() {
        // A light fog of ours hazes the sky; with the land stretched clear to the horizon, the sky is cleared as much
        var given = fog(0F, GAME_END, RENDER_DISTANCE);
        var ours = fog(0F, 600F, RENDER_DISTANCE);
        ours.skyEnd = 600F;
        var target = stretchedByOtherMod(given);
        ExtendedViewFog.apply(target, given, ours, RENDER_DISTANCE);
        assertTrue(target.skyEnd > 600F, "less haze than ours alone: " + target.skyEnd);
        assertTrue(target.skyEnd <= GAME_END, "never clearer than the game's own sky");
    }

    @Test
    void theStartStaysOurs() {
        var given = fog(0F, GAME_END, RENDER_DISTANCE);
        var ours = fog(100F, 500F, RENDER_DISTANCE);
        var target = stretchedByOtherMod(given);
        ExtendedViewFog.apply(target, given, ours, RENDER_DISTANCE);
        assertEquals(100F, target.environmentalStart);
        assertTrue(target.environmentalEnd > 500F);
    }

    @Test
    void badRangesFromTheOtherModAreLeftAlone() {
        assertEquals(1F, ExtendedViewFog.stretch(100F, 0F, 500F, RENDER_DISTANCE));
        assertEquals(1F, ExtendedViewFog.stretch(100F, 500F, Float.POSITIVE_INFINITY, RENDER_DISTANCE));
        assertEquals(1F, ExtendedViewFog.stretch(100F, 500F, Float.NaN, RENDER_DISTANCE));
        assertEquals(1F, ExtendedViewFog.stretch(100F, 500F, 400F, RENDER_DISTANCE), "brought nearer: ours as it is");
        var given = fog(0F, 500F, RENDER_DISTANCE);
        var target = fog(0F, 50F, RENDER_DISTANCE);
        ExtendedViewFog.apply(target, given, fog(0F, 100F, RENDER_DISTANCE), RENDER_DISTANCE);
        assertEquals(50F, target.environmentalEnd, "never thinner than what is there");
    }
}
