package org.orecruncher.dsurround.profiling;

import com.mojang.blaze3d.systems.RenderSystem;
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
 * @param gpu            the graphics card, as the driver names it
 * @param gpuVendor      the graphics card's vendor
 * @param gpuType        the kind of graphics card: discrete, integrated, virtual, cpu or other
 * @param driver         the driver's version, as the backend reports it
 * @param backend        the graphics backend the game runs on: OpenGL or Vulkan
 * @param graphics       the graphics preset, and whether improved transparency (Fabulous's layering) is on
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
record ProfileEnvironment(String mod, int mods, String gpu, String gpuVendor, String gpuType, String driver, String backend,
                          String graphics, boolean shaderPack, int renderDistance, int width, int height, int frameRateLimit,
                          boolean vsync, String devRun, String java, String os, List<ProfileFeature> features) {

    /**
     * The system property naming the development run config, if the game was started with one
     */
    static final String DEV_RUN_PROPERTY = "dsurround.devRun";

    /**
     * The environment now. On the render thread, where the graphics device is asked
     */
    static ProfileEnvironment capture() {
        var mc = Minecraft.getInstance();
        var options = mc.options;
        var window = mc.getWindow();
        var device = RenderSystem.getDevice().getDeviceInfo();
        var platform = Platform.isFabric() ? "Fabric" : "NeoForge";
        return new ProfileEnvironment(
                "%s (%s)".formatted(ContainerManager.resolve(ModInformation.class).getBranding(), platform),
                Platform.getMods().size(),
                device.name(),
                device.vendorName(),
                device.type().name().toLowerCase(Locale.ROOT),
                device.driverInfo(),
                device.backendName(),
                "%s, improved transparency %s".formatted(options.graphicsPreset().get().getSerializedName(),
                        options.improvedTransparency().get() ? "on" : "off"),
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
