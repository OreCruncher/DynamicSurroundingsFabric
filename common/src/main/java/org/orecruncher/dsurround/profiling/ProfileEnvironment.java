package org.orecruncher.dsurround.profiling;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.architectury.platform.Platform;
import net.minecraft.client.Minecraft;
import org.orecruncher.dsurround.lib.compat.IrisCompat;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.platform.ModInformation;

import java.util.Locale;

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
 * @param java           Java's version and vendor
 * @param os             the operating system's name, version and architecture
 */
record ProfileEnvironment(String mod, int mods, String gpu, String gpuVendor, String gpuType, String driver, String backend,
                          String graphics, boolean shaderPack, int renderDistance, String java, String os) {

    /**
     * The environment now. On the render thread, where the graphics device is asked
     */
    static ProfileEnvironment capture() {
        var options = Minecraft.getInstance().options;
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
                javaRuntime(),
                operatingSystem());
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
