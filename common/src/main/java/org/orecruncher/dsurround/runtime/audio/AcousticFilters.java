package org.orecruncher.dsurround.runtime.audio;

import org.orecruncher.dsurround.lib.math.MathStuff;
import org.orecruncher.dsurround.runtime.audio.effects.Efx;

/**
 * Turns what the ray tracing measured for a sound into filter settings: the direct path's gain and cutoff, and a
 * gain and cutoff for each of the four reverb channels (short to long delays). Pure arithmetic, no world access.
 */
final class AcousticFilters {

    static final int CHANNELS = Efx.SENDS;

    /**
     * How strongly blocks in the way muffle a sound.
     */
    private static final float BLOCK_ABSORPTION = 1F;

    // Per reverb channel: how much shared airspace opens up its cutoff, and how strongly bounce ratio scales its
    // gain (channel 0 isn't scaled: x^0 = 1)
    private static final float[] AIRSPACE_DIVISOR = {20F, 15F, 10F, 10F};
    private static final double[] BOUNCE_EXPONENT = {0D, 1D, 3D, 4D};

    /**
     * The filter settings for a sound.
     *
     * @param sendGain     gain of each reverb channel
     * @param sendCutoff   high frequency gain of each reverb channel
     * @param directGain   gain of the direct path
     * @param directCutoff high frequency gain of the direct path
     */
    record Result(float[] sendGain, float[] sendCutoff, float directGain, float directCutoff) {
    }

    private AcousticFilters() {
    }

    /**
     * The bounce ratio for each reverb channel: the total reflectivity at that bounce averaged over the rays. Only
     * as many bounces as were traced; channels beyond that have nothing bouncing into them and get 0.
     *
     * @param bounceTotals reflectivity summed over the rays, per bounce (one entry per configured bounce)
     */
    static float[] channelRatios(float[] bounceTotals, int rays) {
        var ratios = new float[CHANNELS];
        for (int c = 0; c < CHANNELS && c < bounceTotals.length; c++)
            ratios[c] = bounceTotals[c] / rays;
        return ratios;
    }

    /**
     * The shared airspace measure the filters expect: the share of the possible checks that found a clear path from
     * a reflection to the player, scaled so that 64 means all of them.
     *
     * @param clearPaths     checks that reached the player
     * @param possibleChecks how many checks could have been made: rays x bounces when checking every reflection,
     *                       rays when checking only each ray's last
     */
    static float sharedAirspace(float clearPaths, int possibleChecks) {
        // Written as a reciprocal multiply so the every-reflection case gives exactly the values it always has
        return clearPaths * (1F / possibleChecks) * 64F;
    }

    /**
     * @param occlusion      accumulated occlusion between the sound and the player
     * @param sendGains      accumulated reflection energy per reverb channel (not modified)
     * @param bounceRatios   per channel, from {@link #channelRatios}
     * @param sharedAirspace how much of the reflected sound reaches the player directly, scaled
     * @param submersion     what the player's head is in (see {@link Submersion})
     */
    static Result compute(float occlusion, float[] sendGains, float[] bounceRatios, float sharedAirspace,
                          Submersion submersion) {
        final float absorptionCoeff = BLOCK_ABSORPTION * 3.0F;
        final float sendCoeff = -occlusion * absorptionCoeff;
        final float exp1 = (float) Math.exp(sendCoeff);
        final float exp2 = (float) Math.exp(sendCoeff * 1.5F);

        // Shared airspace lets more high frequency through, the shorter channels needing more of it
        var sendCutoff = new float[CHANNELS];
        float weightTotal = 0F;
        for (int c = 0; c < CHANNELS; c++) {
            final float weight = MathStuff.clamp1(sharedAirspace / AIRSPACE_DIVISOR[c]);
            final float exp = c < 2 ? exp1 : exp2;
            sendCutoff[c] = exp * (1.0F - weight) + weight;
            weightTotal += weight;
        }

        // The direct path is muffled by occlusion and dampening, but never below what the shared airspace lets in
        float directCutoff = exp1 * (1F - submersion.directDampening);
        directCutoff = Math.max((float) Math.sqrt(weightTotal * 0.25F) * 0.2F, directCutoff);
        // The ^0.1 keeps the volume up even when the high frequency is cut hard; submersion lowers it as well
        final float directGain = (float) Math.pow(directCutoff, 0.1) * submersion.directGainScale;

        var sendGain = new float[CHANNELS];
        for (int c = 0; c < CHANNELS; c++) {
            float gain = sendGains[c] * (float) Math.pow(bounceRatios[c], BOUNCE_EXPONENT[c]);
            gain = c < 2 ? MathStuff.clamp1(gain) : MathStuff.clamp1(gain * 1.05F - 0.05F);
            sendGain[c] = gain * (float) Math.pow(sendCutoff[c], 0.1) * submersion.sendGainScale;
        }

        // Applied after the gains are worked out from the cutoffs, so only the sound's brightness changes here
        for (int c = 0; c < CHANNELS; c++)
            sendCutoff[c] *= submersion.sendCutoffScale;

        return new Result(sendGain, sendCutoff, directGain, directCutoff);
    }
}
