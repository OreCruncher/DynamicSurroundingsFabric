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
        int later = 120 - ShoreWaveHandler.pulseDelay(60, 0);
        assertFalse(ShoreWaveHandler.pulseHere(0, 60, 0, 120));
        assertTrue(ShoreWaveHandler.pulseHere(later, 60, 0, 120));
    }

    @Test
    void aShoreAcrossTheSweepIsntReachedAllAtOnce() {
        // Along this line x + z doesn't change, so without the ripples every column would take the wave together
        var delays = new java.util.HashSet<Integer>();
        for (int i = 0; i < 40; i++)
            delays.add(ShoreWaveHandler.pulseDelay(i, -i));
        assertTrue(delays.size() > 10, () -> "only " + delays.size() + " different times along the shore");
    }

    @Test
    void theRipplesLeaveNeighborsCloseInTime() {
        // A wave runs along the shore, not breaking up into columns at random
        for (int i = -64; i < 64; i++)
            assertTrue(Math.abs(ShoreWaveHandler.pulseDelay(i + 1, -i - 1) - ShoreWaveHandler.pulseDelay(i, -i)) <= 8);
    }

    @Test
    void theRipplesStayWithinAWavesLength() {
        // Together less than the time between a storm's waves, or one column's next wave would reach it before the last
        // one's neighbor
        assertTrue(ShoreWaveHandler.LONG_RIPPLE_TICKS + ShoreWaveHandler.SHORT_RIPPLE_TICKS < ShoreWaveHandler.STORM_PULSE_TICKS / 2F);
    }

    @Test
    void stormsBringPulsesCloserTogether() {
        assertTrue(ShoreWaveHandler.pulseTicks(1F) < ShoreWaveHandler.pulseTicks(0F));
    }

    @Test
    void aStormThickensTheFoamRatherThanSpreadingIt() {
        // Foam a tick along the shore, over how far it mostly reaches: the storm's has to come out ahead
        assertTrue(foamDensity(1F) > foamDensity(0F) * 2F);
    }

    private static double foamDensity(float storm) {
        double perWave = 1D / ShoreWaveHandler.patchSpacing(storm);
        double perTick = perWave / ShoreWaveHandler.pulseTicks(storm);
        double reach = ShoreWaveHandler.STRAY_SHARE * ShoreWaveHandler.patchReach(storm, 0F)
                + (1F - ShoreWaveHandler.STRAY_SHARE) * ShoreWaveHandler.patchReach(storm, 1F);
        return perTick / reach;
    }

    @Test
    void mostOfAStormsFoamStaysAtTheWatersEdge() {
        assertEquals(ShoreWaveHandler.waveReach(0F), ShoreWaveHandler.patchReach(1F, ShoreWaveHandler.STRAY_SHARE));
        assertEquals(ShoreWaveHandler.waveReach(1F), ShoreWaveHandler.patchReach(1F, ShoreWaveHandler.STRAY_SHARE - 0.01F));
    }

    @Test
    void aCalmDaysFoamAllReachesTheSame() {
        assertEquals(ShoreWaveHandler.patchReach(0F, 0F), ShoreWaveHandler.patchReach(0F, 1F));
    }

    @Test
    void aTorrentGoesNoFurtherThanAStorm() {
        assertEquals(ShoreWaveHandler.waveReach(1F), ShoreWaveHandler.waveReach(1.5F));
    }
}
