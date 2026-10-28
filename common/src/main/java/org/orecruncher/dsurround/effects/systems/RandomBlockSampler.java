package org.orecruncher.dsurround.effects.systems;

import net.minecraft.core.BlockPos;
import org.orecruncher.dsurround.lib.random.IRandomizer;

/**
 * Where {@link RandomBlockEffectSystem} looks each tick. The rate of a block is how many samples land on it a tick,
 * on average.
 * <p>
 * The inner samples are vanilla's animateTick(): {@link #PAIRS} a tick from a triangular spread within
 * {@link #NEAR_RANGE} of the player on each axis, and as many within {@link #FAR_RANGE}. Sounds come from these, so
 * they play as often as they always have.
 * <p>
 * Past {@link #FAR_RANGE} vanilla's spread runs out, so a block effect range set beyond it gets a tail: a rate of
 * {@link #TAIL_RATE} out to {@link #FAR_RANGE}, falling evenly to nothing at the range. A block's rate is the larger
 * of the two, so it falls off smoothly with distance. Tail samples are drawn evenly from the shell between
 * {@link #SHELL_START} and the range, and each is kept with the chance that brings its block up from the inner rate
 * to the tail's.
 */
public final class RandomBlockSampler {

    public static final int NEAR_RANGE = 16;
    public static final int FAR_RANGE = 32;
    /**
     * Inner samples a tick from each of the near and far spreads, as vanilla.
     */
    public static final int PAIRS = 667;
    /**
     * About the inner samples' mean rate {@link #FAR_RANGE} blocks out.
     */
    static final double TAIL_RATE = 1.2e-3;
    /**
     * Every block nearer than this already has an inner rate above {@link #TAIL_RATE} (a test checks), so tail
     * samples needn't go there.
     */
    static final int SHELL_START = 30;

    private RandomBlockSampler() {
    }

    /**
     * Sets {@code out} to a block in the near spread around {@code center}.
     */
    public static void near(IRandomizer random, BlockPos center, BlockPos.MutableBlockPos out) {
        triangle(random, center, NEAR_RANGE, out);
    }

    /**
     * Sets {@code out} to a block in the far spread around {@code center}.
     */
    public static void far(IRandomizer random, BlockPos center, BlockPos.MutableBlockPos out) {
        triangle(random, center, FAR_RANGE, out);
    }

    private static void triangle(IRandomizer random, BlockPos center, int range, BlockPos.MutableBlockPos out) {
        out.set(
                random.triangle(center.getX(), range),
                random.triangle(center.getY(), range),
                random.triangle(center.getZ(), range));
    }

    /**
     * How many tail samples to draw this tick for {@code range}: none within {@link #FAR_RANGE}. The fraction is
     * rounded up or down at random, so the average is right.
     */
    public static int tailDraws(IRandomizer random, int range) {
        if (range <= FAR_RANGE)
            return 0;
        double expected = TAIL_RATE * shellVolume(range);
        int whole = (int) expected;
        return random.nextDouble() < expected - whole ? whole + 1 : whole;
    }

    /**
     * Draws one tail sample into {@code out}; false if it isn't kept, and {@code out} is then meaningless.
     */
    public static boolean tail(IRandomizer random, BlockPos center, int range, BlockPos.MutableBlockPos out) {
        // A point evenly in the shell: the radius by volume, the direction evenly over the sphere
        final double inner3 = (double) SHELL_START * SHELL_START * SHELL_START;
        final double outer3 = (double) range * range * range;
        final double r = Math.cbrt(inner3 + random.nextDouble() * (outer3 - inner3));
        final double cosTheta = 2D * random.nextDouble() - 1D;
        final double sinTheta = Math.sqrt(1D - cosTheta * cosTheta);
        final double phi = 2D * Math.PI * random.nextDouble();

        final int dx = (int) Math.round(r * sinTheta * Math.cos(phi));
        final int dy = (int) Math.round(r * cosTheta);
        final int dz = (int) Math.round(r * sinTheta * Math.sin(phi));

        // Drawn at TAIL_RATE per block; kept for what the inner samples leave short of the tail
        final double shortfall = tailRate(Math.sqrt(dx * dx + dy * dy + dz * dz), range) - innerRate(dx, dy, dz);
        if (shortfall <= 0 || random.nextDouble() * TAIL_RATE >= shortfall)
            return false;

        out.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
        return true;
    }

    /**
     * The inner samples' rate at an offset from the player.
     */
    static double innerRate(int dx, int dy, int dz) {
        return PAIRS * (spread(dx, NEAR_RANGE) * spread(dy, NEAR_RANGE) * spread(dz, NEAR_RANGE)
                + spread(dx, FAR_RANGE) * spread(dy, FAR_RANGE) * spread(dz, FAR_RANGE));
    }

    /**
     * The tail's rate {@code distance} blocks from the player, for a block effect range of {@code range}.
     */
    static double tailRate(double distance, int range) {
        if (range <= FAR_RANGE || distance >= range)
            return 0;
        if (distance <= FAR_RANGE)
            return TAIL_RATE;
        return TAIL_RATE * (range - distance) / (range - FAR_RANGE);
    }

    /**
     * The chance of {@link IRandomizer#triangle} landing {@code offset} from the middle.
     */
    private static double spread(int offset, int range) {
        int weight = range + 1 - Math.abs(offset);
        return weight <= 0 ? 0 : (double) weight / ((range + 1) * (range + 1));
    }

    static double shellVolume(int range) {
        return 4D / 3D * Math.PI * ((double) range * range * range - (double) SHELL_START * SHELL_START * SHELL_START);
    }
}
