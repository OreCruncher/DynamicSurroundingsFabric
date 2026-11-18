// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.profiling;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;
import jdk.jfr.Timespan;

/**
 * A Java Flight Recorder event carrying what one kind of particle cost the CPU over the last second (see
 * {@link ParticleSamples}): one for each of the mod's particle classes that was about, and one for all others together,
 * written once a second while /dsprofile records. Class names and counts only: nothing of where the player is. Only
 * loaded while recording: the {@code jdk.jfr} module may not be there at all in a player's Java.
 */
@Name("dsurround.Particles")
@Label("Particles")
@Category("Dynamic Surroundings")
@Description("What one kind of particle cost the CPU over a second: ticking it, and writing its quads each frame")
@StackTrace(false)
public final class ParticlesEvent extends Event {

    @Label("Type")
    @Description("The mod's particle class, or Others for every particle not the mod's")
    String type;

    @Label("Ticks")
    @Description("Times the particles were ticked in the second")
    long ticks;

    @Label("Frames")
    @Description("Frames the particles were extracted for in the second")
    long frames;

    @Label("Ticked")
    @Description("Particles of the kind ticked, over all the ticks: per tick, how many there were")
    long ticked;

    @Label("Tick Time")
    @Description("Time ticking them, over all the ticks")
    @Timespan(Timespan.NANOSECONDS)
    long tickTime;

    @Label("Extracted")
    @Description("Particles of the kind whose quads were written, over all the frames: those in view")
    long extracted;

    @Label("Extract Time")
    @Description("Time writing their quads, over all the frames")
    @Timespan(Timespan.NANOSECONDS)
    long extractTime;
}
