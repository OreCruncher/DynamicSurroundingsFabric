// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.profiling;

import java.util.concurrent.atomic.LongAdder;

/**
 * What the mod's block and biome queries cost, for the recording /dsprofile is making (see {@link BlockQueriesEvent}):
 * a unit of work's time, each kind on a path that asks the world something different, so a pack that makes one kind of
 * question slower shows as that kind's time a unit against another recording's.
 * <ul>
 *     <li>The block scanner: block states read straight from chunk sections, and the effect systems' look at each.
 *     Slow here is our own handlers, or tag lookups.</li>
 *     <li>Sound traces: each block a ray passes asked for its state, fluid and shape. Slow here is a mod hooking block
 *     shapes or behavior (Camol, in ATM10).</li>
 *     <li>Random block effects: the level's {@code getBlockState}, the chunk looked up each time, then the block's
 *     effects. Slow here is a mod hooking the level's or the chunk source's lookups.</li>
 *     <li>Biome, temperature and precipitation lookups through the level oracle. Slow here is a mod hooking the
 *     game's biome queries.</li>
 *     <li>The season provider's temperature and precipitation questions (whether anything falls, and what), whichever
 *     provider answers: a seasons mod's own hooks (Serene Seasons), or the oracle's lookups without one. Slow here is
 *     the seasons mod.</li>
 * </ul>
 * Work is timed in batches (a tick's scan, a sound's traces), never a block at a time, but for the oracle's lookups,
 * which are one call each. Counted from several threads, so each count is an adder; while nothing records, nothing is
 * counted and nobody reads the clock. Uses nothing from {@code jdk.jfr}.
 */
public final class BlockQuerySamples {

    /**
     * Units of work and their time
     */
    static final class Work {
        private final LongAdder batches = new LongAdder();
        private final LongAdder units = new LongAdder();
        private final LongAdder nanos = new LongAdder();

        void add(long elapsed, long count) {
            this.batches.increment();
            this.units.add(count);
            this.nanos.add(elapsed);
        }

        Taken take() {
            return new Taken(this.batches.sumThenReset(), this.units.sumThenReset(), this.nanos.sumThenReset());
        }
    }

    /**
     * A kind of work's batches, units and their time since the last take
     *
     * @param batches the timed batches (scanner ticks, sound calculations, random block ticks, lookups)
     * @param units   what was worked through in them (blocks, samples, lookups)
     * @param nanos   their time
     */
    record Taken(long batches, long units, long nanos) {
        /**
         * A unit's time, in nanoseconds; 0 with none
         */
        double nanosPerUnit() {
            return this.units == 0 ? 0 : (double) this.nanos / this.units;
        }
    }

    /**
     * A kind of lookup, timed one call at a time: {@code began = begin()}, the lookup, then {@code end(began)}. Reads
     * the clock only while recording, and allocates nothing, as the lookups are on hot paths
     */
    public static final class Lookup {
        private final Work work = new Work();

        private Lookup() {
        }

        /**
         * When the lookup began, by System.nanoTime; 0 while nothing records
         */
        public long begin() {
            return recording ? System.nanoTime() : 0;
        }

        /**
         * Counts the lookup that began at {@code began}, if {@link #begin} timed it
         */
        public void end(long began) {
            if (began != 0)
                this.add(System.nanoTime() - began);
        }

        void add(long nanos) {
            this.work.add(nanos, 1);
        }

        Taken take() {
            return this.work.take();
        }
    }

    /**
     * Everything counted since the last take
     *
     * @param scanner       the block scanner: ticks with blocks read, blocks read
     * @param rays          sound traces: calculations, blocks the rays tested (state, fluid, shape)
     * @param raysTraced    the rays in those calculations
     * @param random        random block effects: ticks, samples
     * @param biome         the oracle's biome lookups
     * @param temperature   the oracle's temperature lookups, the biome's lookup included
     * @param precipitation the oracle's precipitation lookups, the biome's lookup included
     * @param seasonTemperature   the season provider's temperature questions
     * @param seasonPrecipitation the season provider's precipitation questions: whether anything falls, and what
     */
    record Second(Taken scanner, Taken rays, long raysTraced, Taken random, Taken biome, Taken temperature, Taken precipitation,
                  Taken seasonTemperature, Taken seasonPrecipitation) {
    }

    private static final Work SCANNER = new Work();
    private static final Work RAYS = new Work();
    private static final LongAdder RAYS_TRACED = new LongAdder();
    private static final Work RANDOM = new Work();
    /**
     * The oracle's biome lookups
     */
    public static final Lookup BIOME = new Lookup();
    /**
     * The oracle's temperature lookups
     */
    public static final Lookup TEMPERATURE = new Lookup();
    /**
     * The oracle's precipitation lookups
     */
    public static final Lookup PRECIPITATION = new Lookup();
    /**
     * The season provider's temperature questions
     */
    public static final Lookup SEASON_TEMPERATURE = new Lookup();
    /**
     * The season provider's precipitation questions: whether anything falls, or what
     */
    public static final Lookup SEASON_PRECIPITATION = new Lookup();

    private static volatile boolean recording;

    private BlockQuerySamples() {
    }

    /**
     * Whether a recording wants the counts; asked before timing anything
     */
    public static boolean isRecording() {
        return recording;
    }

    /**
     * A scanner tick that read blocks, and its time
     */
    public static void scanned(long nanos, int blocks) {
        if (blocks > 0)
            SCANNER.add(nanos, blocks);
    }

    /**
     * A sound calculation's traces: their time, the rays and the blocks they tested
     */
    public static void traced(long nanos, long rays, long blocks) {
        RAYS.add(nanos, blocks);
        RAYS_TRACED.add(rays);
    }

    /**
     * A random block effects tick: its time and the blocks it sampled
     */
    public static void sampled(long nanos, int samples) {
        RANDOM.add(nanos, samples);
    }

    static Second take() {
        return new Second(SCANNER.take(), RAYS.take(), RAYS_TRACED.sumThenReset(), RANDOM.take(), BIOME.take(),
                TEMPERATURE.take(), PRECIPITATION.take(), SEASON_TEMPERATURE.take(), SEASON_PRECIPITATION.take());
    }

    /**
     * Starts counting, from nothing
     */
    static void start() {
        take();
        recording = true;
    }

    static void stop() {
        recording = false;
    }
}
