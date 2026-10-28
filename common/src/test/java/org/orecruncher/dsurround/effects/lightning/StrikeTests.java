package org.orecruncher.dsurround.effects.lightning;

import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.lib.random.Randomizer;

import static org.junit.jupiter.api.Assertions.*;

public class StrikeTests {

    // A leader of 0.1 seconds, then strokes at 0.1, 0.2 and 0.3
    private static Strike strike() {
        // One patch of glow, lit fully by the first stroke and half by the others
        var glow = new CloudGlow(new float[]{0F, 0F, 0F, 40F}, new float[]{1F, 0.5F, 0.5F}, new int[]{3}, 3, 0.4F);
        return new Strike(Strike.Kind.GROUND, 0D, 200D, 0D, null, 0.1F,
                new float[]{0.1F, 0.2F, 0.3F}, new float[]{1F, 0.8F, 0.8F},
                glow, 100F, 1000D, -1);
    }

    @Test
    void theLeaderShowsOnlyWhereItHasReachedAndFaintly() {
        var strike = strike();
        assertTrue(strike.inLeader(0.05F));
        // Half way across the main channel
        assertEquals(Strike.LEADER_LIGHT, strike.channelBrightness(0, 0.4F, 0.05F));
        assertEquals(0F, strike.channelBrightness(0, 0.6F, 0.05F));
        assertTrue(strike.channelBrightness(1, 0.4F, 0.05F) < Strike.LEADER_LIGHT);
    }

    @Test
    void theReturnStrokeLightsTheWholeChannel() {
        var strike = strike();
        assertFalse(strike.inLeader(0.1F));
        assertEquals(1F, strike.channelBrightness(0, 0F, 0.1F), 1e-6F);
        assertEquals(1F, strike.channelBrightness(0, 1F, 0.1F), 1e-6F);
        // Branches past the end of the main channel too
        assertTrue(strike.channelBrightness(1, 1.3F, 0.1F) > 0.5F);
    }

    @Test
    void eachStrokeDiesAwayThenTheNextLightsItAgain() {
        var strike = strike();
        float peak = strike.channelBrightness(0, 0.5F, 0.1F);
        float faded = strike.channelBrightness(0, 0.5F, 0.199F);
        float second = strike.channelBrightness(0, 0.5F, 0.2F);
        assertTrue(faded < peak * 0.35F, "no flicker: " + faded);
        assertTrue(second > faded + 0.5F, "the second stroke didn't light it");
    }

    @Test
    void laterStrokesBarelyLightTheBranches() {
        var strike = strike();
        float firstMain = strike.channelBrightness(0, 0.5F, 0.1F);
        float firstBranch = strike.channelBrightness(1, 0.5F, 0.1F);
        assertEquals(firstMain * Strike.BRANCH_FALLOFF, firstBranch, 1e-6F);
        // The second stroke relights the main channel brightly, a branch hardly at all
        float mainRelit = strike.channelBrightness(0, 0.5F, 0.2F) - strike.channelBrightness(0, 0.5F, 0.1999F);
        float branchRelit = strike.channelBrightness(1, 0.5F, 0.2F) - strike.channelBrightness(1, 0.5F, 0.1999F);
        assertTrue(mainRelit > 0.7F, "main relit by " + mainRelit);
        assertTrue(branchRelit < 0.1F, "branch relit by " + branchRelit);
        assertTrue(strike.channelBrightness(2, 0.5F, 0.1F) < firstBranch);
    }

    @Test
    void itIsOverAfterTheLastStrokesGlow() {
        var strike = strike();
        assertFalse(strike.isDone(0.3F + Strike.AFTERGLOW - 0.01F));
        assertTrue(strike.isDone(0.3F + Strike.AFTERGLOW + 0.01F));
        assertTrue(strike.channelBrightness(0, 0.5F, 0.3F + Strike.AFTERGLOW) < 0.01F, "still bright at its end");
    }

    @Test
    void ageIsInSecondsFromTheStart() {
        var strike = strike();
        assertEquals(0F, strike.age(1000D));
        assertEquals(0.5F, strike.age(1010D), 1e-6F);
        assertEquals(0F, strike.channelBrightness(0, 0F, strike.age(990D)));
    }

    @Test
    void strokesAreCountedOnceEach() {
        var strike = strike();
        assertEquals(0, strike.strokesBetween(0F, 0.05F));
        assertEquals(1, strike.strokesBetween(0.05F, 0.1F));
        assertEquals(0, strike.strokesBetween(0.1F, 0.15F));
        assertEquals(2, strike.strokesBetween(0.15F, 0.3F));
    }

    @Test
    void theCloudGlowsWithEachStrokeByItsWeight() {
        var strike = strike();
        assertEquals(0F, strike.glowBrightness(0, 0.05F));
        assertEquals(0.4F, strike.glowBrightness(0, 0.1F), 1e-6F);
        // The second stroke, at 0.8 and weighted a half, adds 0.4 x 0.4
        float before = strike.glowBrightness(0, 0.1999F);
        assertEquals(before + 0.16F, strike.glowBrightness(0, 0.2F), 1e-3F);
    }

    @Test
    void strokesFollowTheStorm() {
        var random = Randomizer.create(7L);
        int weakMost = 0, strongMost = 0;
        for (int i = 0; i < 500; i++) {
            weakMost = Math.max(weakMost, Strike.strokeCount(0F, random));
            strongMost = Math.max(strongMost, Strike.strokeCount(1F, random));
        }
        assertEquals(1, weakMost);
        assertEquals(5, strongMost);

        var times = Strike.strokeTimes(0.12F, 4, random);
        assertEquals(0.12F, times[0]);
        for (int i = 1; i < times.length; i++) {
            float gap = times[i] - times[i - 1];
            assertTrue(gap >= 0.04F && gap < 0.13F, "gap " + gap);
        }
        var peaks = Strike.strokePeaks(4, random);
        assertEquals(1F, peaks[0]);
        for (int i = 1; i < peaks.length; i++)
            assertTrue(peaks[i] < 1F);
    }

    @Test
    void aStrikeNeedsAPeakForEachStroke() {
        var oneStroke = new CloudGlow(new float[0], new float[0], new int[0], 1, 1F);
        var twoStrokes = new CloudGlow(new float[0], new float[0], new int[0], 2, 1F);
        assertThrows(IllegalArgumentException.class, () -> new Strike(Strike.Kind.SHEET, 0D, 0D, 0D, null, 0F,
                new float[]{0F, 0.1F}, new float[]{1F}, twoStrokes, 1F, 0D, -1));
        assertThrows(IllegalArgumentException.class, () -> new Strike(Strike.Kind.SHEET, 0D, 0D, 0D, null, 0F,
                new float[0], new float[0], oneStroke, 1F, 0D, -1));
        // And the glow needs a weight for each
        assertThrows(IllegalArgumentException.class, () -> new Strike(Strike.Kind.SHEET, 0D, 0D, 0D, null, 0F,
                new float[]{0F}, new float[]{1F}, twoStrokes, 1F, 0D, -1));
    }
}
