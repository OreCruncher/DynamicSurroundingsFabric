package org.orecruncher.dsurround.effects.aurora;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class AuroraTests {

    private static final int NIGHTS = 2000;

    @Test
    void hasTheOriginalPalettes() {
        // 12 colour sets, then warmer and cooler versions of 6 of them; 4 of the warmer ones are already as bright as
        // they can be, so are the same as their originals and left out
        assertEquals(20, AuroraPalette.PALETTES.size());
        assertEquals(AuroraPalette.PALETTES.size(), new java.util.HashSet<>(AuroraPalette.PALETTES).size());
        for (var palette : AuroraPalette.PALETTES) {
            for (var c : new AuroraPalette.Rgb[]{palette.bottom(), palette.middle(), palette.top()}) {
                assertTrue(c.red() >= 0F && c.red() <= 1F);
                assertTrue(c.green() >= 0F && c.green() <= 1F);
                assertTrue(c.blue() >= 0F && c.blue() <= 1F);
            }
        }
    }

    @Test
    void colourFromRgb() {
        var indigo = AuroraPalette.Rgb.of(0x4B0082);
        assertEquals(75 / 255F, indigo.red(), 1e-6);
        assertEquals(0F, indigo.green(), 1e-6);
        assertEquals(130 / 255F, indigo.blue(), 1e-6);
    }

    @Test
    void luminanceScalesEachChannelAndClamps() {
        var c = new AuroraPalette.Rgb(0.5F, 1F, 0F);
        var warmer = c.luminance(0.3F);
        assertEquals(0.65F, warmer.red(), 1e-6);
        assertEquals(1F, warmer.green(), 1e-6);
        assertEquals(0F, warmer.blue(), 1e-6);
        var cooler = c.luminance(-0.3F);
        assertEquals(0.35F, cooler.red(), 1e-6);
        assertEquals(0.7F, cooler.green(), 1e-6);
    }

    @Test
    void neverOrAlwaysAtTheExtremes() {
        for (long night = 0; night < NIGHTS; night++) {
            assertFalse(Aurora.appears(night, 0));
            assertTrue(Aurora.appears(night, 100));
        }
    }

    @Test
    void appearsOnAboutTheChosenShareOfNights() {
        for (int chance : new int[]{10, 33, 50, 80}) {
            int count = 0;
            for (long night = 0; night < 10_000; night++)
                if (Aurora.appears(night, chance))
                    count++;
            assertEquals(chance / 100D, count / 10_000D, 0.03, "chance " + chance);
        }
    }

    @Test
    void consecutiveNightsDontRepeat() {
        // A run of the same answer for many nights in a row would mean the night number isn't mixed well
        int longestRun = 0, run = 0;
        boolean last = false;
        for (long night = 0; night < 10_000; night++) {
            var now = Aurora.appears(night, 50);
            run = now == last ? run + 1 : 1;
            last = now;
            longestRun = Math.max(longestRun, run);
        }
        assertTrue(longestRun < 25, "longest run " + longestRun);
    }

    @Test
    void sameNightSameAurora() {
        for (long night = 0; night < 100; night++)
            assertEquals(Aurora.create(night, 3), Aurora.create(night, 3));
        assertNotEquals(Aurora.create(1, 3), Aurora.create(2, 3));
    }

    @Test
    void usesManyPalettes() {
        var seen = new java.util.HashSet<AuroraPalette>();
        for (long night = 0; night < NIGHTS; night++)
            seen.add(Aurora.create(night, 3).palette());
        assertEquals(AuroraPalette.PALETTES.size(), seen.size());
    }

    @Test
    void bandsWithinTheLimit() {
        for (int maxBands = 1; maxBands <= 3; maxBands++) {
            var seen = new boolean[4];
            for (long night = 0; night < NIGHTS; night++) {
                var bands = Aurora.create(night, maxBands).bands();
                assertTrue(bands >= 1 && bands <= maxBands);
                seen[bands] = true;
            }
            for (int i = 1; i <= maxBands; i++)
                assertTrue(seen[i], "never " + i + " bands with at most " + maxBands);
        }
    }

    @Test
    void curtainsStayInsideTheSkyAndAboveTheHorizon() {
        // Inside the stars and the nearest far plane, however the curtains fold and ripple
        var bottom = new Vector3f();
        var top = new Vector3f();
        float farthest = 0F;
        for (long night = 0; night < NIGHTS; night++) {
            var aurora = Aurora.create(night, 3);
            for (int band = 0; band < aurora.bands(); band++) {
                for (int i = 0; i <= 40; i++) {
                    float s = -1F + i / 20F;
                    for (float time = 0F; time < 600F; time += 37.3F) {
                        aurora.bottom(band, s, time, bottom);
                        aurora.top(band, bottom, top);
                        farthest = Math.max(farthest, Math.max(bottom.length(), top.length()));
                        assertTrue(bottom.y > 0.5F, "lower edge too low");
                        assertTrue(top.y > bottom.y, "top below the lower edge");
                    }
                }
            }
        }
        assertTrue(farthest <= Aurora.MAX_REACH, "reaches " + farthest + " sky units, " + farthest * Aurora.SCALE + " blocks");
    }

    @Test
    void liesToTheNorth() {
        var bottom = new Vector3f();
        for (long night = 0; night < NIGHTS; night++) {
            var aurora = Aurora.create(night, 3);
            assertTrue(Math.abs(aurora.heading()) <= Aurora.MAX_HEADING);
            // The middle of the first curtain is north of the viewer (-Z), however it folds and ripples
            for (float time = 0F; time < 600F; time += 37.3F)
                assertTrue(aurora.bottom(0, 0F, time, bottom).z < 0F);
        }
    }

    @Test
    void brightnessIsOneOfTheOriginalLevels() {
        var seen = new java.util.HashSet<Float>();
        for (long night = 0; night < NIGHTS; night++) {
            var brightness = Aurora.create(night, 3).brightness();
            assertTrue(brightness == 0.50F || brightness == 0.63F || brightness == 0.75F, "brightness " + brightness);
            seen.add(brightness);
        }
        assertEquals(3, seen.size(), "not every level is used");
    }

    @Test
    void taperFadesOnlyTowardTheEnds() {
        assertEquals(1F, Aurora.taper(0F), 1e-6);
        assertEquals(1F, Aurora.taper(0.5F), 1e-6);
        assertEquals(0F, Aurora.taper(1F), 1e-6);
        assertEquals(0F, Aurora.taper(-1F), 1e-6);
        assertEquals(Aurora.taper(0.8F), Aurora.taper(-0.8F), 1e-6);
        float last = 1F;
        for (int i = 0; i <= 100; i++) {
            var t = Aurora.taper(i / 100F);
            assertTrue(t <= last + 1e-6);
            last = t;
        }
    }
}
