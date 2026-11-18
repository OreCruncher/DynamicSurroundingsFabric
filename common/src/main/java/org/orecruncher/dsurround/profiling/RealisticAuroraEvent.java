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
 * A Java Flight Recorder event carrying what the realistic aurora was over the last second and how many pixels its
 * shader marched (see {@link RealisticAuroraSamples}), written once a second while one is drawn and /dsprofile
 * records, so its GPU time can be set against how much work there was. What it was is the night's, the same for
 * everyone in a world, and the rest is the window's size and the view's pitch: nothing of where the player is. Only
 * loaded while recording: the {@code jdk.jfr} module may not be there at all in a player's Java.
 */
@Name("dsurround.RealisticAurora")
@Label("Realistic Aurora")
@Category("Dynamic Surroundings")
@Description("The realistic aurora over a second: what the night made of it, how finely it was drawn, and the pixels its shader marched")
@StackTrace(false)
public final class RealisticAuroraEvent extends Event {

    @Label("Frames")
    @Description("Frames it was drawn in")
    int frames;

    @Label("Activity")
    @Description("How active the night is (quiet to storm)")
    String activity;

    @Label("Color")
    @Description("The night's colors")
    String color;

    @Label("Phase")
    @Description("Where it was in its night, as of the last frame (growth, breakup, expansion, recovery)")
    String phase;

    @Label("Arcs")
    @Description("Arcs side by side (1 to 4)")
    int arcs;

    @Label("Ray Field")
    @Description("How strong the ray field was, 0 (none) to 1, with the night's gain, mean of the frames")
    float rayField;

    @Label("Quality")
    @Description("How finely it was drawn (Fast: half size, Fancy: full)")
    String quality;

    @Label("Field Samples")
    @Description("Samples of the ray field a step (more at Fast, to make up for its size)")
    int fieldSamples;

    @Label("Width")
    @Description("Width in pixels of what it was drawn to, as of the last frame")
    int width;

    @Label("Height")
    @Description("Height in pixels of what it was drawn to, as of the last frame")
    int height;

    @Label("Strength")
    @Description("How strongly it was drawn, 0 to 1, mean of the frames")
    float strength;

    @Label("Marched Share")
    @Description("Share of the pixels the shader marched (those looking above the horizon), mean of the frames")
    double marchedShare;

    @Label("Pixels Marched")
    @Description("Pixels the shader marched a frame, mean")
    double pixels;
}
