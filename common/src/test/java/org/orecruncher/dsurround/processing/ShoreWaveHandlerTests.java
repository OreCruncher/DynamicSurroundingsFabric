// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.processing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ShoreWaveHandlerTests {

    @Test
    void wavesAreSmallButThereInFairWeather() {
        assertTrue(ShoreWaveHandler.waveReach(0F) > 0F);
    }

    @Test
    void aStormBuildsThemUp() {
        assertTrue(ShoreWaveHandler.waveReach(1F) > ShoreWaveHandler.waveReach(0.2F));
    }

    @Test
    void aPulseReachesAColumnOnceAPeriod() {
        int hits = 0;
        for (long tick = 0; tick < 120; tick++)
            if (ShoreWaveHandler.pulseHere(tick, 5, 9, 120))
                hits++;
        assertEquals(1, hits);
    }

    @Test
    void aPulseSweepsAlongTheCoast() {
        assertTrue(ShoreWaveHandler.pulseHere(0, 0, 0, 120));
        // Far enough along, it isn't there yet
        assertFalse(ShoreWaveHandler.pulseHere(0, 20, 0, 120));
        assertTrue(ShoreWaveHandler.pulseHere(110, 20, 0, 120));
    }

    @Test
    void stormsBringPulsesCloserTogether() {
        assertTrue(ShoreWaveHandler.pulseTicks(1F) < ShoreWaveHandler.pulseTicks(0F));
    }

    @Test
    void aTorrentGoesNoFurtherThanAStorm() {
        assertEquals(ShoreWaveHandler.waveReach(1F), ShoreWaveHandler.waveReach(1.5F));
    }
}
