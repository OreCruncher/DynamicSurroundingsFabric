// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.runtime.weather;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests how a rain sound is played on a surface: the calm, medium and heavy rains' volumes, the pitch, the filter, and
 * the surface's own rain in place of part of it.
 */
public class RainMixTests {

    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final float EPSILON = 1.0E-5F;

    @Test
    void withNoSurfaceTheStormsSharesAreVanillasVolume() {
        var mix = RainMix.of(null, 0.8F, 0.9F, 0.6F, true);
        assertEquals(0.8F * StormSounds.calmShare(0.6F), mix.calmVolume(), EPSILON);
        assertEquals(0.8F * StormSounds.mediumShare(0.6F), mix.mediumVolume(), EPSILON);
        assertEquals(0.8F * StormSounds.heavyShare(0.6F), mix.heavyVolume(), EPSILON);
        assertEquals(0.9F, mix.pitch(), EPSILON);
        assertNull(mix.filter());
        assertNull(mix.layer());
        assertEquals(0F, mix.layerVolume());
    }

    @Test
    void aSurfacesFilterIsUsedOnlyWhereOneCanBeSet() {
        // Wood has a filter and no layer of its own: filtered, it takes the filtered volume and pitch
        var filtered = RainMix.of(RainSurface.WOOD, 1F, 1F, 1F, true);
        assertSame(RainSurface.WOOD.filter(), filtered.filter());
        assertEquals(RainSurface.WOOD.pitch(true), filtered.pitch(), EPSILON);
        assertEquals(RainSurface.WOOD.volume(true) * StormSounds.calmShare(1F), filtered.calmVolume(), EPSILON);
        // Without enhanced sound processing, by volume and pitch alone
        var plain = RainMix.of(RainSurface.WOOD, 1F, 1F, 1F, false);
        assertNull(plain.filter());
        assertEquals(RainSurface.WOOD.pitch(false), plain.pitch(), EPSILON);
        assertEquals(RainSurface.WOOD.volume(false) * StormSounds.calmShare(1F), plain.calmVolume(), EPSILON);
    }

    @Test
    void aSurfacesOwnRainTakesPartOfTheRainsPlace() {
        var mix = RainMix.of(RainSurface.METAL, 1F, 1F, 0.5F, true);
        assertEquals(RainSurface.METAL.layer(), mix.layer());
        float onMetal = RainSurface.METAL.volume(true);
        assertEquals(onMetal * StormSounds.layerShare(0.5F), mix.layerVolume(), EPSILON);
        assertEquals(onMetal * StormSounds.keptShare() * StormSounds.calmShare(0.5F), mix.calmVolume(), EPSILON);
        assertEquals(onMetal * StormSounds.keptShare() * StormSounds.heavyShare(0.5F), mix.heavyVolume(), EPSILON);
    }
}
