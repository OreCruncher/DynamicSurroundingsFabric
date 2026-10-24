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

    @Label("GPU")
    String gpu;

    @Label("GPU vendor")
    String gpuVendor;

    @Label("GPU type")
    String gpuType;

    @Label("Driver")
    String driver;

    @Label("Backend")
    String backend;

    @Label("Graphics")
    String graphics;

    @Label("Shader pack")
    boolean shaderPack;

    @Label("Render distance")
    int renderDistance;

    @Label("Java")
    String java;

    @Label("Operating system")
    String os;

    static void write(ProfileEnvironment environment) {
        var event = new EnvironmentEvent();
        event.mod = environment.mod();
        event.mods = environment.mods();
        event.gpu = environment.gpu();
        event.gpuVendor = environment.gpuVendor();
        event.gpuType = environment.gpuType();
        event.driver = environment.driver();
        event.backend = environment.backend();
        event.graphics = environment.graphics();
        event.shaderPack = environment.shaderPack();
        event.renderDistance = environment.renderDistance();
        event.java = environment.java();
        event.os = environment.os();
        event.commit();
    }
}
