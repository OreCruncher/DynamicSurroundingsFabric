package org.orecruncher.dsurround.profiling;

import com.mojang.blaze3d.platform.GlUtil;
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
 * @param glRenderer     the graphics card, as the driver names it
 * @param glVendor       the driver's vendor
 * @param glVersion      the OpenGL version, which on most drivers includes the driver's version
 * @param graphics       the graphics setting: fast, fancy or fabulous
 * @param shaderPack     whether a shader pack is in use
 * @param renderDistance the render distance in chunks, as the game uses it
 * @param java           Java's version and vendor
 * @param os             the operating system's name, version and architecture
 */
record ProfileEnvironment(String mod, int mods, String glRenderer, String glVendor, String glVersion, String graphics,
                          boolean shaderPack, int renderDistance, String java, String os) {

    /**
     * The environment now. On the render thread: the graphics card is asked through OpenGL
     */
    static ProfileEnvironment capture() {
        var options = Minecraft.getInstance().options;
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
