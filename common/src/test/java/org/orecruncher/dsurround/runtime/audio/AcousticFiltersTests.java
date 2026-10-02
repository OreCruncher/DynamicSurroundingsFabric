package org.orecruncher.dsurround.runtime.audio;

import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.lib.math.MathStuff;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class AcousticFiltersTests {

    private static final int RAYS = 32;

    /**
     * The arithmetic as it was in SoundFXUtils.calculate before it moved to AcousticFilters, copied verbatim
     * (with 4 bounces, the only count it worked with).
     */
    private static AcousticFilters.Result original(float occlusionAccumulation, float[] gains, float[] bounceTotals,
                                                   float sharedAirspace, float auralDampening, boolean underwater) {
        final float absorptionCoeff = 1F * 3.0F;
        final float sendCoeff = -occlusionAccumulation * absorptionCoeff;
        float directCutoff = (float) Math.exp(sendCoeff);
        directCutoff *= 1F - auralDampening;

        float sendGain0 = gains[0], sendGain1 = gains[1], sendGain2 = gains[2], sendGain3 = gains[3];
        float sendCutoff0, sendCutoff1, sendCutoff2, sendCutoff3;
        final float[] bounceRatio = bounceTotals.clone();

        bounceRatio[0] = bounceRatio[0] / RAYS;
        bounceRatio[1] = bounceRatio[1] / RAYS;
        bounceRatio[2] = bounceRatio[2] / RAYS;
        bounceRatio[3] = bounceRatio[3] / RAYS;

        final float sharedAirspaceWeight0 = MathStuff.clamp1(sharedAirspace / 20.0F);
        final float sharedAirspaceWeight1 = MathStuff.clamp1(sharedAirspace / 15.0F);
        final float sharedAirspaceWeight2 = MathStuff.clamp1(sharedAirspace / 10.0F);
        final float sharedAirspaceWeight3 = MathStuff.clamp1(sharedAirspace / 10.0F);

        final float exp1 = (float) Math.exp(sendCoeff);
        final float exp2 = (float) Math.exp(sendCoeff * 1.5F);
        sendCutoff0 = exp1 * (1.0F - sharedAirspaceWeight0) + sharedAirspaceWeight0;
        sendCutoff1 = exp1 * (1.0F - sharedAirspaceWeight1) + sharedAirspaceWeight1;
        sendCutoff2 = exp2 * (1.0F - sharedAirspaceWeight2) + sharedAirspaceWeight2;
        sendCutoff3 = exp2 * (1.0F - sharedAirspaceWeight3) + sharedAirspaceWeight3;

        final float averageSharedAirspace = (sharedAirspaceWeight0 + sharedAirspaceWeight1 + sharedAirspaceWeight2
                + sharedAirspaceWeight3) * 0.25F;
        directCutoff = Math.max((float) Math.sqrt(averageSharedAirspace) * 0.2F, directCutoff);

        float directGain = (float) Math.pow(directCutoff, 0.1);

        sendGain1 *= bounceRatio[1];
        sendGain2 *= (float) Math.pow(bounceRatio[2], 3.0);
        sendGain3 *= (float) Math.pow(bounceRatio[3], 4.0);

        sendGain0 = MathStuff.clamp1(sendGain0);
        sendGain1 = MathStuff.clamp1(sendGain1);
        sendGain2 = MathStuff.clamp1(sendGain2 * 1.05F - 0.05F);
        sendGain3 = MathStuff.clamp1(sendGain3 * 1.05F - 0.05F);

        sendGain0 *= (float) Math.pow(sendCutoff0, 0.1);
        sendGain1 *= (float) Math.pow(sendCutoff1, 0.1);
        sendGain2 *= (float) Math.pow(sendCutoff2, 0.1);
        sendGain3 *= (float) Math.pow(sendCutoff3, 0.1);

        if (underwater) {
            sendCutoff0 *= 0.4F;
            sendCutoff1 *= 0.4F;
            sendCutoff2 *= 0.4F;
            sendCutoff3 *= 0.4F;
        }

        return new AcousticFilters.Result(
                new float[]{sendGain0, sendGain1, sendGain2, sendGain3},
                new float[]{sendCutoff0, sendCutoff1, sendCutoff2, sendCutoff3},
                directGain, directCutoff);
    }

    private static AcousticFilters.Result compute(float occlusion, float[] gains, float[] bounceTotals,
                                                  float sharedAirspace, Submersion submersion) {
        return AcousticFilters.compute(occlusion, gains, AcousticFilters.channelRatios(bounceTotals, RAYS),
                sharedAirspace, submersion);
    }

    private static float[] randomArray(Random random, int length, float max) {
        var array = new float[length];
        for (int i = 0; i < length; i++)
            array[i] = random.nextFloat() * max;
        return array;
    }

    // ---- Same results as before ------------------------------------------------------------------------------

    @Test
    void identicalToTheOriginalArithmeticOutOfWater() {
        // The submerged values were retuned; out of water the results must be exactly as before
        var random = new Random(2024);
        for (int i = 0; i < 20_000; i++) {
            float occlusion = random.nextInt(4) == 0 ? 0F : random.nextFloat() * 5F;
            float[] gains = randomArray(random, 4, 3F);
            float[] bounceTotals = randomArray(random, 4, RAYS);
            float airspace = random.nextInt(4) == 0 ? 0F : random.nextFloat() * 40F;

            var expected = original(occlusion, gains, bounceTotals, airspace, 0F, false);
            var actual = compute(occlusion, gains, bounceTotals, airspace, Submersion.NONE);

            // Exact: the same float operations in the same order
            assertArrayEquals(expected.sendGain(), actual.sendGain(), "send gain, case " + i);
            assertArrayEquals(expected.sendCutoff(), actual.sendCutoff(), "send cutoff, case " + i);
            assertEquals(expected.directGain(), actual.directGain(), "direct gain, case " + i);
            assertEquals(expected.directCutoff(), actual.directCutoff(), "direct cutoff, case " + i);
        }
    }

    @Test
    void inputsAreNotModified() {
        float[] gains = {1F, 2F, 3F, 4F};
        compute(1F, gains, new float[]{8F, 8F, 8F, 8F}, 5F, Submersion.NONE);

        assertArrayEquals(new float[]{1F, 2F, 3F, 4F}, gains);
    }

    // ---- Bounce counts ---------------------------------------------------------------------------------------

    @Test
    void everyConfigurableBounceCountWorks() {
        // Regression: with 2 or 3 bounces the old code indexed past the end of its bounce array on every
        // calculation, and the exception was silently swallowed
        for (int bounces = 2; bounces <= 8; bounces++) {
            float[] totals = new float[bounces];
            java.util.Arrays.fill(totals, 16F);

            var ratios = AcousticFilters.channelRatios(totals, RAYS);
            var result = AcousticFilters.compute(0.5F, new float[]{1F, 1F, 1F, 1F}, ratios, 4F, Submersion.NONE);

            assertEquals(AcousticFilters.CHANNELS, ratios.length, "bounces " + bounces);
            for (int c = 0; c < AcousticFilters.CHANNELS; c++)
                assertEquals(c < bounces ? 0.5F : 0F, ratios[c], "bounces " + bounces + " channel " + c);
            assertEquals(AcousticFilters.CHANNELS, result.sendGain().length);
        }
    }

    @Test
    void channelsBeyondTheBounceCountAreSilent() {
        var ratios = AcousticFilters.channelRatios(new float[]{16F, 16F}, RAYS);

        var result = AcousticFilters.compute(0F, new float[]{1F, 1F, 1F, 1F}, ratios, 0F, Submersion.NONE);

        assertTrue(result.sendGain()[0] > 0F);
        assertTrue(result.sendGain()[1] > 0F);
        assertEquals(0F, result.sendGain()[2]);
        assertEquals(0F, result.sendGain()[3]);
    }

    // ---- Behaviour -------------------------------------------------------------------------------------------

    @Test
    void unobstructedSoundIsUnfiltered() {
        // With the exact exp, nothing in the way means full direct signal
        var result = compute(0F, new float[4], new float[4], 0F, Submersion.NONE);

        assertEquals(1F, result.directCutoff());
        assertEquals(1F, result.directGain());
    }

    @Test
    void occlusionMuffles() {
        float previous = 2F;
        for (float occlusion = 0F; occlusion <= 3F; occlusion += 0.5F) {
            var cutoff = compute(occlusion, new float[4], new float[4], 0F, Submersion.NONE).directCutoff();
            assertTrue(cutoff < previous, "occlusion " + occlusion);
            previous = cutoff;
        }
    }

    @Test
    void submersionMufflesAndQuietens() {
        var air = compute(0.2F, new float[4], new float[4], 0F, Submersion.NONE);
        var water = compute(0.2F, new float[4], new float[4], 0F, Submersion.WATER);
        var lava = compute(0.2F, new float[4], new float[4], 0F, Submersion.LAVA);

        assertTrue(water.directCutoff() < air.directCutoff());
        assertTrue(lava.directCutoff() < water.directCutoff());
        assertTrue(water.directGain() < air.directGain());
        assertTrue(lava.directGain() < water.directGain());
    }

    @Test
    void unobstructedSoundUnderWater() {
        // Regression: the cutoff was 0.4 (about -8 dB) and the volume 0.91, barely different from in air
        var result = compute(0F, new float[4], new float[4], 0F, Submersion.WATER);

        assertEquals(0.15F, result.directCutoff(), 1.0E-6, "about -16 dB of high frequency");
        assertEquals((float) Math.pow(0.15F, 0.1) * 0.6F, result.directGain(), 1.0E-6);
        assertTrue(result.directGain() < 0.5F, "audibly quieter, about -6 dB");
    }

    @Test
    void sharedAirspaceOpensTheCutoffs() {
        var enclosed = compute(2F, new float[4], new float[4], 0F, Submersion.NONE);
        var open = compute(2F, new float[4], new float[4], 40F, Submersion.NONE);

        for (int c = 0; c < AcousticFilters.CHANNELS; c++) {
            assertTrue(open.sendCutoff()[c] > enclosed.sendCutoff()[c], "channel " + c);
            assertEquals(1F, open.sendCutoff()[c], 1.0E-6, "fully open, channel " + c);
        }
    }

    @Test
    void submersionScalesTheReverbSends() {
        float[] gains = {1F, 1F, 1F, 1F};
        float[] totals = {16F, 16F, 16F, 16F};
        var dry = compute(1F, gains, totals, 5F, Submersion.NONE);

        for (var medium : new Submersion[]{Submersion.WATER, Submersion.LAVA}) {
            var wet = compute(1F, gains, totals, 5F, medium);
            for (int c = 0; c < AcousticFilters.CHANNELS; c++) {
                assertEquals(dry.sendCutoff()[c] * medium.sendCutoffScale, wet.sendCutoff()[c], 1.0E-6, medium + " cutoff " + c);
                assertEquals(dry.sendGain()[c] * medium.sendGainScale, wet.sendGain()[c], 1.0E-6, medium + " gain " + c);
            }
        }
    }

    @Test
    void notSubmergedChangesNothing() {
        var medium = Submersion.NONE;

        assertEquals(0F, medium.directDampening);
        assertEquals(1F, medium.directGainScale);
        assertEquals(1F, medium.sendGainScale);
        assertEquals(1F, medium.sendCutoffScale);
    }

    @Test
    void outputsStayWithinRange() {
        var random = new Random(7);
        for (int i = 0; i < 5_000; i++) {
            var result = compute(random.nextFloat() * 10F, randomArray(random, 4, 10F), randomArray(random, 4, RAYS),
                    random.nextFloat() * 100F, Submersion.values()[random.nextInt(Submersion.values().length)]);

            for (int c = 0; c < AcousticFilters.CHANNELS; c++) {
                assertTrue(result.sendGain()[c] >= 0F && result.sendGain()[c] <= 1F);
                assertTrue(result.sendCutoff()[c] >= 0F && result.sendCutoff()[c] <= 1F);
            }
            assertTrue(result.directGain() >= 0F && result.directGain() <= 1F);
            assertTrue(result.directCutoff() >= 0F && result.directCutoff() <= 1F);
        }
    }
}
