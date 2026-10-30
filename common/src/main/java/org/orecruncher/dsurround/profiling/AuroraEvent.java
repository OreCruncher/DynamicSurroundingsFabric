package org.orecruncher.dsurround.profiling;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

/**
 * A Java Flight Recorder event carrying the classic aurora's shape and how much of the screen it covered over the last
 * second (see {@link AuroraSamples}), written once a second while one is drawn and /dsprofile records, so its GPU time
 * can be set against how much of it there was to draw. The shape is the night's, the same for everyone in a world:
 * nothing of where the player is. Only loaded while recording: the {@code jdk.jfr} module may not be there at all in a
 * player's Java.
 */
@Name("dsurround.Aurora")
@Label("Aurora")
@Category("Dynamic Surroundings")
@Description("The classic aurora's shape and how much of the screen it covered over a second")
@StackTrace(false)
public final class AuroraEvent extends Event {

    @Label("Frames")
    @Description("Frames it was drawn in")
    int frames;

    @Label("Bands")
    @Description("Curtains, side by side (1 to 3)")
    int bands;

    @Label("Distance")
    @Description("How far its path is from the viewer, in sky units (1.5 to 4)")
    float distance;

    @Label("Length")
    @Description("How long its path is, in sky units (6 to 10)")
    float length;

    @Label("Altitude")
    @Description("How high its lower edge is, in sky units (0.8 to 1.2)")
    float altitude;

    @Label("Height")
    @Description("How tall the first curtain is, in sky units (0.8 to 1.4)")
    float height;

    @Label("Heading")
    @Description("Which way it lies, in degrees from north")
    float heading;

    @Label("Strength")
    @Description("How strongly it was drawn, 0 to 1 (the shader's alpha), mean of the frames")
    float strength;

    @Label("Pixels")
    @Description("Pixels its quads covered a frame, mean, counted as often as bands overlap")
    double pixels;

    @Label("Screen Share")
    @Description("Pixels as a share of the screen's; more than 1 where bands overlap")
    double screenShare;
}
