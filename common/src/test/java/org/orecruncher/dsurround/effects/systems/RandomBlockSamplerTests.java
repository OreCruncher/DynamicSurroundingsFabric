package org.orecruncher.dsurround.effects.systems;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.lib.random.IRandomizer;
import org.orecruncher.dsurround.lib.random.Randomizer;

import static org.junit.jupiter.api.Assertions.*;
import static org.orecruncher.dsurround.effects.systems.RandomBlockSampler.*;

/**
 * Tests for where random block effects look: vanilla's near and far spreads, and the tail past them out to the block
 * effect range.
 */
public class RandomBlockSamplerTests {

    private static final BlockPos CENTER = new BlockPos(100, 64, -200);

    /**
     * A block's rate from both kinds of sample, as the sampler intends.
     */
    private static double totalRate(int dx, int dy, int dz, int range) {
        return Math.max(innerRate(dx, dy, dz), tailRate(Math.sqrt(dx * dx + dy * dy + dz * dz), range));
    }

    /**
     * The mean rate of the blocks {@code distance} to {@code distance + 1} from the player, by 1-block shells.
     */
    private static double[] shellMeans(int range, boolean innerOnly) {
        int reach = Math.max(range, FAR_RANGE * 2);
        int shells = reach + 1;
        double[] sums = new double[shells];
        int[] counts = new int[shells];
        for (int x = -reach; x <= reach; x++)
            for (int y = -reach; y <= reach; y++)
                for (int z = -reach; z <= reach; z++) {
                    int shell = (int) Math.sqrt(x * x + y * y + z * z);
                    if (shell >= shells)
                        continue;
                    sums[shell] += innerOnly ? innerRate(x, y, z) : totalRate(x, y, z, range);
                    counts[shell]++;
                }
        for (int i = 0; i < shells; i++)
            sums[i] /= counts[i];
        return sums;
    }

    @Test
    void innerRatesAddUpToTheSamplesTaken() {
        double sum = 0;
        for (int x = -FAR_RANGE; x <= FAR_RANGE; x++)
            for (int y = -FAR_RANGE; y <= FAR_RANGE; y++)
                for (int z = -FAR_RANGE; z <= FAR_RANGE; z++)
                    sum += innerRate(x, y, z);
        assertEquals(2 * PAIRS, sum, 1e-6);
    }

    @Test
    void innerSamplesFollowTheirRate() {
        // Hits near the player against what innerRate() says
        final int ticks = 2000;
        final int box = 4;
        IRandomizer random = Randomizer.create(1);
        var pos = new BlockPos.MutableBlockPos();
        int hits = 0;
        for (int t = 0; t < ticks; t++)
            for (int i = 0; i < PAIRS; i++) {
                near(random, CENTER, pos);
                if (withinBox(pos, box))
                    hits++;
                far(random, CENTER, pos);
                if (withinBox(pos, box))
                    hits++;
            }

        double expected = 0;
        for (int x = -box; x <= box; x++)
            for (int y = -box; y <= box; y++)
                for (int z = -box; z <= box; z++)
                    expected += innerRate(x, y, z);
        expected *= ticks;
        assertEquals(expected, hits, expected * 0.02);
    }

    private static boolean withinBox(BlockPos pos, int box) {
        return Math.abs(pos.getX() - CENTER.getX()) <= box
                && Math.abs(pos.getY() - CENTER.getY()) <= box
                && Math.abs(pos.getZ() - CENTER.getZ()) <= box;
    }

    @Test
    void tailNeedNotReachInsideTheShell() {
        // Tail samples are drawn from SHELL_START out. A block a little inside it can still be drawn, rounded in,
        // but those nearer than that never are, so the inner samples must already give them the tail's rate.
        final int limit = SHELL_START + 1;
        for (int x = -limit; x <= limit; x++)
            for (int y = -limit; y <= limit; y++)
                for (int z = -limit; z <= limit; z++)
                    if (x * x + y * y + z * z < limit * limit)
                        assertTrue(innerRate(x, y, z) >= TAIL_RATE, "%d, %d, %d".formatted(x, y, z));
    }

    @Test
    void tailStartsAtTheInnerRateThere() {
        double mean = shellMeans(FAR_RANGE, true)[FAR_RANGE];
        assertEquals(mean, TAIL_RATE, mean * 0.1);
    }

    @Test
    void rateFallsOffWithDistance() {
        for (int range : new int[]{16, 32, 33, 40, 48, 64}) {
            // No denser ring anywhere on the way out; three samplers made one past FAR_RANGE
            double[] means = shellMeans(range, false);
            for (int shell = 1; shell < range; shell++)
                assertTrue(means[shell] <= means[shell - 1] * 1.001,
                        "shell %d: %.3e after %.3e".formatted(shell, means[shell], means[shell - 1]));
        }
    }

    @Test
    void noTailWithinVanillaRange() {
        for (int range : new int[]{16, 32}) {
            IRandomizer random = Randomizer.create(2);
            for (int i = 0; i < 100; i++)
                assertEquals(0, tailDraws(random, range));
        }
    }

    @Test
    void tailDrawsAverageTheShellsRate() {
        for (int range : new int[]{33, 40, 64}) {
            IRandomizer random = Randomizer.create(3);
            final int ticks = 10000;
            long draws = 0;
            for (int i = 0; i < ticks; i++)
                draws += tailDraws(random, range);
            double expected = TAIL_RATE * shellVolume(range);
            assertEquals(expected, (double) draws / ticks, Math.max(expected * 0.01, 0.01));
        }
    }

    @Test
    void tailMakesUpTheShortfall() {
        for (int range : new int[]{40, 64}) {
            // Kept tail samples, by 1-block shell, against what the inner samples leave short of the tail's rate
            final int ticks = 3000;
            IRandomizer random = Randomizer.create(4);
            var pos = new BlockPos.MutableBlockPos();
            long[] kept = new long[range + 1];
            for (int t = 0; t < ticks; t++) {
                int draws = tailDraws(random, range);
                for (int i = 0; i < draws; i++)
                    if (tail(random, CENTER, range, pos)) {
                        double distance = Math.sqrt(pos.distSqr(CENTER));
                        assertTrue(distance < range, "kept a sample %.1f blocks out".formatted(distance));
                        kept[(int) distance]++;
                    }
            }

            double[] shortfall = new double[range + 1];
            for (int x = -range; x <= range; x++)
                for (int y = -range; y <= range; y++)
                    for (int z = -range; z <= range; z++) {
                        double distance = Math.sqrt(x * x + y * y + z * z);
                        if (distance < range)
                            shortfall[(int) distance] += Math.max(0, tailRate(distance, range) - innerRate(x, y, z));
                    }

            // The outermost shell's blocks reach past the sphere the samples are drawn from, so they get a few less;
            // the tail is all but gone there. It and shells with only a handful expected are left to the overall count.
            for (int shell = 0; shell < range - 1; shell++) {
                double expected = shortfall[shell] * ticks;
                if (expected < 2000)
                    continue;
                assertEquals(expected, kept[shell], expected * 0.05, "shell " + shell);
            }

            double expectedAll = 0;
            long keptAll = 0;
            for (int shell = 0; shell < range; shell++) {
                expectedAll += shortfall[shell] * ticks;
                keptAll += kept[shell];
            }
            assertEquals(expectedAll, keptAll, expectedAll * 0.02);
        }
    }
}
