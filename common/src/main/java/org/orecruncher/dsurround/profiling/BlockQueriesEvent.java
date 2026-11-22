// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.profiling;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

/**
 * A Java Flight Recorder event: what the mod's block and biome queries cost over a second (see
 * {@link BlockQuerySamples}), each kind as a count and its time, so recordings can be compared by a unit's time.
 * Only loaded while recording: the {@code jdk.jfr} module may not be there at all in a player's Java.
 */
@Name("dsurround.BlockQueries")
@Label("Block Queries")
@Category("Dynamic Surroundings")
@Description("What the mod's block and biome queries cost over a second: each kind's count and time, for its time a block or lookup")
@StackTrace(false)
public final class BlockQueriesEvent extends Event {

    @Label("Scanner Ticks")
    @Description("Block scanner ticks that read blocks")
    int scannerTicks;

    @Label("Scanner Blocks")
    @Description("Blocks the scanner read from chunk sections, and handed to the effect systems")
    long scannerBlocks;

    @Label("Scanner Time")
    @Description("The scanner's time over those ticks, in nanoseconds")
    long scannerNanos;

    @Label("Trace Calculations")
    @Description("Enhanced sound calculations that traced rays")
    int traceCalculations;

    @Label("Rays")
    @Description("Rays those calculations traced")
    long rays;

    @Label("Ray Blocks")
    @Description("Blocks the rays tested: each block's state, fluid and shape")
    long rayBlocks;

    @Label("Trace Time")
    @Description("The calculations' tracing time, in nanoseconds")
    long traceNanos;

    @Label("Random Ticks")
    @Description("Random block effect ticks")
    int randomTicks;

    @Label("Random Samples")
    @Description("Blocks random block effects sampled through the level")
    long randomSamples;

    @Label("Random Time")
    @Description("Random block effects' time over those ticks, effects and sounds started included, in nanoseconds")
    long randomNanos;

    @Label("Biome Lookups")
    long biomeLookups;

    @Label("Biome Time")
    @Description("The biome lookups' time, in nanoseconds")
    long biomeNanos;

    @Label("Temperature Lookups")
    long temperatureLookups;

    @Label("Temperature Time")
    @Description("The temperature lookups' time, their biome lookup included, in nanoseconds")
    long temperatureNanos;

    @Label("Precipitation Lookups")
    long precipitationLookups;

    @Label("Precipitation Time")
    @Description("The precipitation lookups' time, their biome lookup included, in nanoseconds")
    long precipitationNanos;

    static void write(BlockQuerySamples.Second second) {
        var event = new BlockQueriesEvent();
        event.scannerTicks = (int) second.scanner().batches();
        event.scannerBlocks = second.scanner().units();
        event.scannerNanos = second.scanner().nanos();
        event.traceCalculations = (int) second.rays().batches();
        event.rays = second.raysTraced();
        event.rayBlocks = second.rays().units();
        event.traceNanos = second.rays().nanos();
        event.randomTicks = (int) second.random().batches();
        event.randomSamples = second.random().units();
        event.randomNanos = second.random().nanos();
        event.biomeLookups = second.biome().units();
        event.biomeNanos = second.biome().nanos();
        event.temperatureLookups = second.temperature().units();
        event.temperatureNanos = second.temperature().nanos();
        event.precipitationLookups = second.precipitation().units();
        event.precipitationNanos = second.precipitation().nanos();
        event.commit();
    }
}
