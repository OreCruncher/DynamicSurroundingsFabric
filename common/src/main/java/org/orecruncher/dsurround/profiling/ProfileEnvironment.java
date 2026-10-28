package org.orecruncher.dsurround.profiling;

import com.mojang.blaze3d.platform.GlUtil;
import dev.architectury.platform.Platform;
import net.minecraft.client.Minecraft;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.lib.compat.IrisCompat;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.platform.ModInformation;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * What a recording was made on, written into it once (see {@link EnvironmentEvent}): the times in a recording mean
 * little without the graphics card and settings they were measured with. Nothing in it names the player, their folders
 * or their machine: the graphics card and driver are what the F3 screen and every crash report show, and of the
 * system only Java's version and the operating system's name and version are taken (the JDK's own events for those are
 * left out of recordings, for what else they carry; see {@link ProfileRecording#PRIVATE}). Uses nothing from
 * {@code jdk.jfr}.
 *
 * @param mod            the mod's version, with Minecraft's and the platform
 * @param mods           how many mods are installed
 * @param glRenderer     the graphics card, as the driver names it
 * @param glVendor       the driver's vendor
 * @param glVersion      the OpenGL version, which on most drivers includes the driver's version
 * @param graphics       the graphics setting: fast, fancy or fabulous
 * @param shaderPack     whether a shader pack is in use
 * @param renderDistance the render distance in chunks, as the game uses it
 * @param width          the frame's width in pixels, as drawn (the window's, not scaled for the screen's settings)
 * @param height         the frame's height in pixels, as drawn
 * @param frameRateLimit the frame rate limit setting; 260 is unlimited
 * @param vsync          whether frames wait for the display
 * @param devRun         which of the development environment's run configs the game was started with (the
 *                       {@code dsurround.devRun} property, set by the "potato" ones that stand in for a low-end PC; see
 *                       build.gradle); empty for a player's game
 * @param java           Java's version and vendor
 * @param os             the operating system's name, version and architecture
 * @param features       the optional hooks, the features that stand aside for other mods, and the settings
 */
record ProfileEnvironment(String mod, int mods, String glRenderer, String glVendor, String glVersion, String graphics,
                          boolean shaderPack, int renderDistance, int width, int height, int frameRateLimit,
                          boolean vsync, String devRun, String java, String os, List<ProfileFeature> features) {

    /**
     * The system property naming the development run config, if the game was started with one
     */
    static final String DEV_RUN_PROPERTY = "dsurround.devRun";

    /**
     * The environment now. On the render thread: the graphics card is asked through OpenGL
     */
    static ProfileEnvironment capture() {
        var mc = Minecraft.getInstance();
        var options = mc.options;
        var window = mc.getWindow();
        var platform = Platform.isFabric() ? "Fabric" : "NeoForge";
        return new ProfileEnvironment(
                "%s (%s)".formatted(ContainerManager.resolve(ModInformation.class).getBranding(), platform),
                Platform.getMods().size(),
                GlUtil.getRenderer(),
                GlUtil.getVendor(),
                GlUtil.getOpenGLVersion(),
                options.graphicsMode().get().name().toLowerCase(Locale.ROOT),
                IrisCompat.isShaderPackInUse(),
                options.getEffectiveRenderDistance(),
                window.getWidth(),
                window.getHeight(),
                options.framerateLimit().get(),
                options.enableVsync().get(),
                Objects.requireNonNullElse(System.getProperty(DEV_RUN_PROPERTY), ""),
                javaRuntime(),
                operatingSystem(),
                ProfileFeature.capture(ContainerManager.resolve(Configuration.class)));
    }

    /**
     * Java's version and vendor, from the system properties
     */
    static String javaRuntime() {
        return "%s (%s)".formatted(System.getProperty("java.version"), System.getProperty("java.vendor"));
    }

    /**
     * The operating system's name, version and architecture, from the system properties
     */
    static String operatingSystem() {
        return "%s %s %s".formatted(System.getProperty("os.name"), System.getProperty("os.version"), System.getProperty("os.arch"));
    }
}
