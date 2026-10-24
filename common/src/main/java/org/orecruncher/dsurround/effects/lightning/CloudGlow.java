package org.orecruncher.dsurround.effects.lightning;

import net.minecraft.util.Mth;
import org.orecruncher.dsurround.lib.random.IRandomizer;

/**
 * The cloud lit up by a strike: patches of light lying flat along the cloud base, each broken up by the cloud's own
 * texture as it is drawn. Each stroke lights the patches by its own weights, so in a flash hidden in the cloud the
 * light moves about inside it from one stroke to the next, rather than one round glow fading and coming back.
 */
public final class CloudGlow {

    // x, y, z (from where the strike starts) and radius of each patch
    private static final int STRIDE = 4;
    /**
     * The fewest a patch is lit by a stroke, against the hottest, in a flash hidden in the cloud
     */
    static final float LEAST_LIT = 0.15F;
    // Noise cells for each patch's texture are picked from this many, so neighbouring patches look different
    private static final int NOISE_CELLS = 64;

    private final float[] patches;
    // Patch by stroke: how much each stroke lights each patch, 0 to 1
    private final float[] weights;
    private final int[] noiseCells;
    private final int strokes;
    private final float strength;

    CloudGlow(float[] patches, float[] weights, int[] noiseCells, int strokes, float strength) {
        int count = patches.length / STRIDE;
        if (patches.length % STRIDE != 0 || noiseCells.length != count || weights.length != count * strokes)
            throw new IllegalArgumentException("a weight for each patch and stroke, and a noise cell for each patch");
        this.patches = patches;
        this.weights = weights;
        this.noiseCells = noiseCells;
        this.strokes = strokes;
        this.strength = strength;
    }

    /**
     * A flash hidden in the cloud: several patches spread out around where it is. Each stroke lights one of them
     * hottest, and the others less the farther from it they are.
     *
     * @param spread how far from the middle patches lie, at most, in blocks
     */
    static CloudGlow sheet(float spread, int strokes, float strength, IRandomizer random) {
        int count = 4 + random.nextInt(4);
        var patches = new float[count * STRIDE];
        for (int p = 0; p < count; p++) {
            // The first in the middle; the rest anywhere within the spread, more of them out toward its edge
            float angle = random.nextFloat() * Mth.TWO_PI;
            float out = p == 0 ? 0F : Mth.sqrt(random.nextFloat()) * spread;
            patches[p * STRIDE] = Mth.cos(angle) * out;
            patches[p * STRIDE + 1] = random.nextFloat(-3F, 3F);
            patches[p * STRIDE + 2] = Mth.sin(angle) * out;
            patches[p * STRIDE + 3] = random.nextFloat(25F, 55F);
        }
        var weights = new float[count * strokes];
        for (int s = 0; s < strokes; s++) {
            int hot = random.nextInt(count);
            for (int p = 0; p < count; p++) {
                float dx = patches[p * STRIDE] - patches[hot * STRIDE];
                float dz = patches[p * STRIDE + 2] - patches[hot * STRIDE + 2];
                float apart = Mth.sqrt(dx * dx + dz * dz);
                float w = p == hot ? 1F : Math.max(LEAST_LIT, 1F - apart / (spread * 1.2F)) * random.nextFloat(0.8F, 1F);
                weights[p * strokes + s] = w;
            }
        }
        return new CloudGlow(patches, weights, noiseCells(count, random), strokes, strength);
    }

    /**
     * Patches around given points: where a bolt leaves the cloud, along spider lightning. Every stroke lights them
     * much the same: the channel lights its own stretch of cloud.
     *
     * @param centres x, y, z of each, from where the strike starts
     */
    static CloudGlow around(float[] centres, float minRadius, float maxRadius, int strokes, float strength, IRandomizer random) {
        int count = centres.length / 3;
        var patches = new float[count * STRIDE];
        for (int p = 0; p < count; p++) {
            patches[p * STRIDE] = centres[p * 3] + random.nextFloat(-8F, 8F);
            patches[p * STRIDE + 1] = centres[p * 3 + 1];
            patches[p * STRIDE + 2] = centres[p * 3 + 2] + random.nextFloat(-8F, 8F);
            patches[p * STRIDE + 3] = random.nextFloat(minRadius, maxRadius);
        }
        var weights = new float[count * strokes];
        for (int p = 0; p < count; p++)
            for (int s = 0; s < strokes; s++)
                weights[p * strokes + s] = s == 0 ? 1F : random.nextFloat(0.6F, 1F);
        return new CloudGlow(patches, weights, noiseCells(count, random), strokes, strength);
    }

    private static int[] noiseCells(int count, IRandomizer random) {
        var cells = new int[count];
        for (int p = 0; p < count; p++)
            cells[p] = random.nextInt(NOISE_CELLS);
        return cells;
    }

    public int count() {
        return this.noiseCells.length;
    }

    public float x(int patch) {
        return this.patches[patch * STRIDE];
    }

    public float y(int patch) {
        return this.patches[patch * STRIDE + 1];
    }

    public float z(int patch) {
        return this.patches[patch * STRIDE + 2];
    }

    public float radius(int patch) {
        return this.patches[patch * STRIDE + 3];
    }

    /**
     * Which part of the noise texture the patch's cloud texture comes from
     */
    public int noiseCell(int patch) {
        return this.noiseCells[patch];
    }

    /**
     * How much a stroke lights a patch, 0 to 1
     */
    public float weight(int patch, int stroke) {
        return this.weights[patch * this.strokes + stroke];
    }

    public int strokes() {
        return this.strokes;
    }

    /**
     * How bright the glow is at a stroke's peak, against the channel's 1
     */
    public float strength() {
        return this.strength;
    }
}
