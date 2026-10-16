package org.orecruncher.dsurround.processing.accents;

import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.sound.ISoundFactory;
import org.orecruncher.dsurround.testing.Fakes;

import java.util.ArrayList;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for how loud a step splashes on ground in the rain, and for the accents a step gathers.
 */
public class WaterySurfaceAccentTests {

    @Test
    void aDrizzleDoesNotSplash() {
        assertEquals(0F, WaterySurfaceAccent.rainVolume(0F));
        assertEquals(0F, WaterySurfaceAccent.rainVolume(WaterySurfaceAccent.DRY_BELOW - 0.01F));
    }

    @Test
    void theSplashGrowsWithTheStorm() {
        assertEquals(WaterySurfaceAccent.QUIETEST, WaterySurfaceAccent.rainVolume(WaterySurfaceAccent.DRY_BELOW), 1e-6F);
        assertEquals(1F, WaterySurfaceAccent.rainVolume(1F), 1e-6F);
        assertTrue(WaterySurfaceAccent.rainVolume(0.4F) < WaterySurfaceAccent.rainVolume(0.8F));
        // A storm surging past full strength splashes no louder
        assertEquals(1F, WaterySurfaceAccent.rainVolume(1.2F), 1e-6F);
    }

    @Test
    void lightRainSplashesOnFewerSteps() {
        assertEquals(0F, WaterySurfaceAccent.splashChance(WaterySurfaceAccent.DRY_BELOW - 0.01F));
        assertEquals(WaterySurfaceAccent.FEWEST, WaterySurfaceAccent.splashChance(WaterySurfaceAccent.DRY_BELOW), 1e-6F);
        assertEquals(1F, WaterySurfaceAccent.splashChance(1F), 1e-6F);
        assertEquals(1F, WaterySurfaceAccent.splashChance(1.2F), 1e-6F);
        // A quarter-strength storm: well under half the steps splash, at about a third of the volume
        assertTrue(WaterySurfaceAccent.splashChance(0.25F) < 0.45F);
        assertTrue(WaterySurfaceAccent.rainVolume(0.25F) < 0.4F);
    }

    @Test
    void accentsKeepTheirVolumes() {
        var accents = new StepAccents();
        var factories = new ArrayList<ISoundFactory>();
        for (int i = 0; i < 6; i++) {
            var factory = Fakes.of(ISoundFactory.class, Map.of());
            factories.add(factory);
            if (i % 2 == 0)
                accents.add(factory);
            else
                accents.add(factory, i / 10F);
        }

        var seen = new ArrayList<Object[]>();
        accents.forEach((factory, volumeScale) -> seen.add(new Object[]{factory, volumeScale}));
        assertEquals(6, seen.size());
        for (int i = 0; i < 6; i++) {
            assertSame(factories.get(i), seen.get(i)[0]);
            assertEquals(i % 2 == 0 ? 1F : i / 10F, (float) seen.get(i)[1], 1e-6F);
        }

        accents.clear();
        assertEquals(0, accents.size());
    }
}
