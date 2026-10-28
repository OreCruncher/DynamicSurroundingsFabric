package org.orecruncher.dsurround.profiling;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * Where random block effects looked each tick and what they found there, by distance from the player, for the
 * recording /dsprofile is making (see {@link RandomBlockSamplesEvent}); while nothing records, the system doesn't count.
 * Uses nothing from {@code jdk.jfr}, so the system can call it in a Java without that module.
 * <p>
 * A tick's counts are one array, {@link #KINDS} to a 1-block shell: {@code counts[shell * KINDS + kind]}.
 */
public final class RandomBlockSamples {

    /**
     * 1-block shells from the player out to the largest block effect range; anything farther is counted in the last
     */
    public static final int SHELLS = 65;
    /**
     * Inner samples (vanilla's near and far spreads)
     */
    public static final int SAMPLES = 0;
    /**
     * Tail samples kept (past vanilla's spreads, out to the block effect range)
     */
    public static final int TAIL = 1;
    /**
     * Samples, of either kind, that landed on a block with effects configured and none running there
     */
    public static final int EFFECT_BLOCKS = 2;
    /**
     * Effects started that keep running (a flame jet, bubbles)
     */
    public static final int STARTS = 3;
    /**
     * Sounds played
     */
    public static final int SOUNDS = 4;
    /**
     * Particles spawned outright, with nothing kept running (fireflies, say)
     */
    public static final int PARTICLES = 5;
    public static final int KINDS = 6;

    @FunctionalInterface
    interface Sink {
        void ticked(int range, int[] counts);
    }

    private static volatile @Nullable Sink sink;

    private RandomBlockSamples() {
    }

    /**
     * Whether a recording wants the counts; asked once a tick
     */
    public static boolean isRecording() {
        return sink != null;
    }

    /**
     * Where {@code pos}'s count goes for {@code kind}
     */
    public static int index(BlockPos pos, BlockPos center, int kind) {
        int shell = Math.min((int) Math.sqrt(pos.distSqr(center)), SHELLS - 1);
        return shell * KINDS + kind;
    }

    /**
     * One tick's counts, from the client thread; copied, so the array can be reused
     *
     * @param range the block effect range in effect
     */
    public static void ticked(int range, int[] counts) {
        var current = sink;
        if (current != null)
            current.ticked(range, counts);
    }

    static void start(Sink recording) {
        sink = recording;
    }

    static void stop() {
        sink = null;
    }
}
