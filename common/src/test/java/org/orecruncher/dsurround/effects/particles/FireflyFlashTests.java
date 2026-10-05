package org.orecruncher.dsurround.effects.particles;

import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;

import static org.junit.jupiter.api.Assertions.*;

public class FireflyFlashTests {

    private static final int SEEDS = 200;

    private static float peakNear(FireflyFlash flash, float start) {
        float peak = 0F;
        for (float t = start; t < start + 10F; t += 0.25F)
            peak = Math.max(peak, flash.brightness(t));
        return peak;
    }

    @Test
    void darkBeforeTheFirstFlash() {
        for (long seed = 0; seed < SEEDS; seed++) {
            var flash = new FireflyFlash(RandomSource.create(seed));
            var first = flash.pulseStarts()[0];
            assertTrue(first >= 5F && first <= 25F, "first flash at " + first);
            for (float t = 0F; t <= first; t += 0.5F)
                assertEquals(0F, flash.brightness(t));
        }
    }

    @Test
    void eachPulseReachesFullBrightness() {
        for (long seed = 0; seed < SEEDS; seed++) {
            var flash = new FireflyFlash(RandomSource.create(seed));
            for (var start : flash.pulseStarts())
                assertEquals(1F, peakNear(flash, start), 0.01F);
        }
    }

    @Test
    void mostlyDark() {
        // A real firefly is dark most of the time: count how much of its life it is more than faintly lit (the
        // fading tail of each flash counts as lit, so a short-lived double flasher is lit the most)
        for (long seed = 0; seed < SEEDS; seed++) {
            var flash = new FireflyFlash(RandomSource.create(seed));
            int lit = 0;
            for (int t = 0; t < flash.lifetime(); t++)
                if (flash.brightness(t) > 0.1F)
                    lit++;
            var share = lit / (float) flash.lifetime();
            assertTrue(share < 0.45F, flash.pattern() + " lit " + share + " of the time");
        }
    }

    @Test
    void darkBetweenFlashes() {
        for (long seed = 0; seed < SEEDS; seed++) {
            var flash = new FireflyFlash(FireflyFlash.Pattern.SINGLE, RandomSource.create(seed));
            var starts = flash.pulseStarts();
            for (int i = 1; i < starts.length; i++) {
                var midway = (starts[i - 1] + starts[i]) / 2F;
                assertTrue(flash.brightness(midway) < 0.01F);
            }
        }
    }

    @Test
    void flashesQuickerThanTheyFade() {
        var flash = new FireflyFlash(FireflyFlash.Pattern.SINGLE, RandomSource.create(1));
        var start = flash.pulseStarts()[0];
        // Up to full in 3 ticks; still lit a few ticks after
        assertEquals(1F, flash.brightness(start + 3F), 1e-4);
        assertTrue(flash.brightness(start + 6F) > 0.3F);
        assertTrue(flash.brightness(start + 1.5F) < flash.brightness(start + 4.5F));
    }

    @Test
    void doubleFlashesComeInPairs() {
        for (long seed = 0; seed < SEEDS; seed++) {
            var flash = new FireflyFlash(FireflyFlash.Pattern.DOUBLE, RandomSource.create(seed));
            var starts = flash.pulseStarts();
            assertEquals(0, starts.length % 2);
            for (int i = 0; i < starts.length; i += 2) {
                assertEquals(FireflyFlash.Pattern.DOUBLE.pulseGap, starts[i + 1] - starts[i], 1e-6);
                // Dimmed between the two pulses, then lit again
                assertTrue(flash.brightness(starts[i + 1]) < 0.2F);
            }
        }
    }

    @Test
    void periodsMatchTheirKind() {
        for (var pattern : FireflyFlash.Pattern.values()) {
            for (long seed = 0; seed < SEEDS; seed++) {
                var starts = new FireflyFlash(pattern, RandomSource.create(seed)).pulseStarts();
                for (int i = pattern.pulses; i < starts.length; i += pattern.pulses) {
                    var period = starts[i] - starts[i - pattern.pulses];
                    assertTrue(period >= pattern.minPeriod - pattern.jitter && period <= pattern.maxPeriod + pattern.jitter,
                            pattern + " period " + period);
                }
                var flashes = starts.length / pattern.pulses;
                assertTrue(flashes >= pattern.minFlashes && flashes <= pattern.maxFlashes);
            }
        }
    }

    @Test
    void livesUntilItsLastFlashHasFaded() {
        for (long seed = 0; seed < SEEDS; seed++) {
            var flash = new FireflyFlash(RandomSource.create(seed));
            var starts = flash.pulseStarts();
            assertTrue(flash.lifetime() > starts[starts.length - 1]);
            assertTrue(flash.brightness(flash.lifetime()) < 0.01F, "still lit when it goes");
        }
    }

    @Test
    void dipsBeforeAFlashAndRisesWhileLit() {
        var flash = new FireflyFlash(FireflyFlash.Pattern.SINGLE, RandomSource.create(5));
        var start = flash.pulseStarts()[0];
        assertTrue(flash.climb(start - 3F) < 0F, "no dip before the flash");
        assertTrue(flash.climb(start + 3F) > 0F, "no rise while lit");
        assertEquals(0F, flash.climb(0F));
    }

    @Test
    void allKindsAppear() {
        var counts = new EnumMap<FireflyFlash.Pattern, Integer>(FireflyFlash.Pattern.class);
        for (long seed = 0; seed < 1000; seed++)
            counts.merge(new FireflyFlash(RandomSource.create(seed)).pattern(), 1, Integer::sum);
        assertEquals(FireflyFlash.Pattern.values().length, counts.size());
        // Single flashers are the most common
        assertTrue(counts.get(FireflyFlash.Pattern.SINGLE) > counts.get(FireflyFlash.Pattern.QUICK));
    }
}
