package org.orecruncher.dsurround.profiling;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;

/**
 * A Java Flight Recorder event carrying what a recording was made on (see {@link ProfileEnvironment}), written once as
 * /dsprofile starts recording. Only loaded while recording: the {@code jdk.jfr} module may not be there at all in a
 * player's Java.
 */
@Name("dsurround.Environment")
@Label("Environment")
@Category("Dynamic Surroundings")
@Description("The graphics card, settings and versions a Dynamic Surroundings profile was recorded with")
public final class EnvironmentEvent extends Event {

    @Label("Mod")
    String mod;

    @Label("Mods installed")
    int mods;

    @Label("GL renderer")
    String glRenderer;

    @Label("GL vendor")
    String glVendor;

    @Label("GL version")
    String glVersion;

    @Label("Graphics")
    String graphics;

    @Label("Shader pack")
    boolean shaderPack;

    @Label("Render distance")
    int renderDistance;

    @Label("Width")
    @Description("The frame's width in pixels, as drawn")
    int width;

    @Label("Height")
    @Description("The frame's height in pixels, as drawn")
    int height;

    @Label("Frame rate limit")
    @Description("The frame rate limit setting; 260 is unlimited")
    int frameRateLimit;

    @Label("VSync")
    boolean vsync;

    @Label("Development run")
    @Description("The development environment's run config the game was started with (the potato ones stand in for a "
            + "low-end PC); empty for a player's game")
    String devRun;

    @Label("Java")
    String java;

    @Label("Operating system")
    String os;

    static void write(ProfileEnvironment environment) {
        var event = new EnvironmentEvent();
        event.mod = environment.mod();
        event.mods = environment.mods();
        event.glRenderer = environment.glRenderer();
        event.glVendor = environment.glVendor();
        event.glVersion = environment.glVersion();
        event.graphics = environment.graphics();
        event.shaderPack = environment.shaderPack();
        event.renderDistance = environment.renderDistance();
        event.width = environment.width();
        event.height = environment.height();
        event.frameRateLimit = environment.frameRateLimit();
        event.vsync = environment.vsync();
        event.devRun = environment.devRun();
        event.java = environment.java();
        event.os = environment.os();
        event.commit();
    }
}
