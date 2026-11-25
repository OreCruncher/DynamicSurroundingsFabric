// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.processing.accents;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.runtime.audio.FixedFilter;
import org.orecruncher.dsurround.runtime.weather.GroundWetness;
import org.orecruncher.dsurround.runtime.weather.RainSurface;
import org.orecruncher.dsurround.sound.ISoundFactory;
import org.orecruncher.dsurround.testing.Fakes;

import java.util.ArrayList;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.orecruncher.dsurround.processing.accents.WaterySurfaceAccent.*;

/**
 * Tests for what a step on wet ground adds, by what is underfoot and how wet it is, and for the accents a step gathers.
 */
public class WaterySurfaceAccentTests {

    static {
        // RainSurface sorts by the game's sound types
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void groundThatTurnsToMudSquelchesHardGroundSlapsTheRestIsQuiet() {
        // Grass and dirt sort as soft to the rain; turning to mud is what counts
        assertEquals(WetGround.MUD, wetGround(RainSurface.SOFT, true));
        assertEquals(WetGround.MUD, wetGround(RainSurface.HARD, true));
        for (var hard : new RainSurface[]{RainSurface.HARD, RainSurface.WOOD, RainSurface.METAL, RainSurface.GLASS})
            assertEquals(WetGround.SLAP, wetGround(hard, false), hard.name());
        // Sand, gravel, snow and leaves take the rain in
        for (var quiet : new RainSurface[]{RainSurface.SOFT, RainSurface.LEAVES, RainSurface.WATER, RainSurface.HOT})
            assertEquals(WetGround.NONE, wetGround(quiet, false), quiet.name());
    }

    @Test
    void barelyWetHardGroundIsSilentThenSlapsLouder() {
        assertEquals(0F, slapVolume(0F));
        assertEquals(0F, slapVolume(DRY_BELOW - 0.01F));
        assertEquals(SLAP_QUIETEST, slapVolume(DRY_BELOW), 1e-6F);
        assertEquals(SLAP_LOUDEST, slapVolume(1F), 1e-6F);
        assertTrue(slapVolume(0.4F) < slapVolume(0.8F));
        // Past full strength, no more than at it
        assertEquals(SLAP_LOUDEST, slapVolume(1.2F), 1e-6F);
    }

    @Test
    void aSlapIsWellUnderASplash() {
        // A splash is at full volume
        assertTrue(slapVolume(1F) <= 0.5F);
        // Thinned by taking the lows away, a little higher
        assertEquals(FixedFilter.highPass(0.2F), SLAP_FILTER);
        assertTrue(SLAP_PITCH > 1F && SLAP_PITCH < SLAP_PITCH_UNFILTERED && SLAP_PITCH_UNFILTERED < 1.5F,
                "past 1.5 a sound sounds sped up");
        assertTrue(SLAP_VOLUME_UNFILTERED < 1F);
    }

    @Test
    void groundSquelchesOnceMuddyAsItLeavesMudPrints() {
        assertEquals(0F, mudVolume(GroundWetness.MUDDY - 0.01F));
        assertEquals(MUD_QUIETEST, mudVolume(GroundWetness.MUDDY), 1e-6F);
        assertEquals(1F, mudVolume(1F), 1e-6F);
        assertTrue(mudVolume(0.5F) < mudVolume(0.9F));
        // Barely wet hard ground can slap where grass isn't muddy yet
        assertTrue(DRY_BELOW < GroundWetness.MUDDY);
    }

    @Test
    void accentsKeepTheirVolumesPitchesAndFilters() {
        var accents = new StepAccents();
        var factories = new ArrayList<ISoundFactory>();
        var filter = FixedFilter.highPass(0.5F);
        for (int i = 0; i < 6; i++) {
            var factory = Fakes.of(ISoundFactory.class, Map.of());
            factories.add(factory);
            switch (i % 3) {
                case 0 -> accents.add(factory);
                case 1 -> accents.add(factory, i / 10F);
                default -> accents.add(factory, i / 10F, 1.2F, filter);
            }
        }

        var seen = new ArrayList<Object[]>();
        accents.forEach((factory, volumeScale, pitchScale, f) -> seen.add(new Object[]{factory, volumeScale, pitchScale, f}));
        assertEquals(6, seen.size());
        for (int i = 0; i < 6; i++) {
            assertSame(factories.get(i), seen.get(i)[0]);
            assertEquals(i % 3 == 0 ? 1F : i / 10F, (float) seen.get(i)[1], 1e-6F);
            assertEquals(i % 3 == 2 ? 1.2F : 1F, (float) seen.get(i)[2], 1e-6F);
            assertSame(i % 3 == 2 ? filter : null, seen.get(i)[3]);
        }

        accents.clear();
        assertEquals(0, accents.size());
    }
}
