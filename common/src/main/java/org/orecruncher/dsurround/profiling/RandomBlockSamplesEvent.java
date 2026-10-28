package org.orecruncher.dsurround.profiling;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

/**
 * A Java Flight Recorder event carrying what random block effects did in one 1-block shell around the player over the
 * last second (see {@link RandomBlockSamples}), written once a second for each shell with anything in it while
 * /dsprofile records, so how densely they look and start effects can be checked by distance. Distances only: nothing
 * of where the player is. Only loaded while recording: the {@code jdk.jfr} module may not be there at all in a
 * player's Java.
 */
@Name("dsurround.RandomBlockSamples")
@Label("Random Block Samples")
@Category("Dynamic Surroundings")
@Description("What Dynamic Surroundings' random block effects did at one distance from the player over a second")
@StackTrace(false)
public final class RandomBlockSamplesEvent extends Event {

    @Label("Shell")
    @Description("Distance from the player in whole blocks: this shell holds the blocks from it to one block farther")
    int shell;

    @Label("Ticks")
    @Description("Client ticks counted; the same for every shell of the second")
    int ticks;

    @Label("Range")
    @Description("The block effect range at the end of the second")
    int range;

    @Label("Samples")
    @Description("Inner samples (vanilla's near and far spreads)")
    long samples;

    @Label("Tail Samples")
    @Description("Tail samples kept, past vanilla's spreads out to the block effect range")
    long tailSamples;

    @Label("Effect Blocks")
    @Description("Samples that landed on a block with effects configured and none running there")
    long effectBlocks;

    @Label("Starts")
    @Description("Effects started that keep running (a flame jet, bubbles)")
    long starts;

    @Label("Sounds")
    @Description("Sounds played")
    long sounds;

    @Label("Particles")
    @Description("Particles spawned outright, with nothing kept running (fireflies, say)")
    long particles;
}
