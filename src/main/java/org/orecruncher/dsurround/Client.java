package org.orecruncher.dsurround;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.MinecraftClient;
import org.orecruncher.dsurround.commands.Commands;
import org.orecruncher.dsurround.config.*;
import org.orecruncher.dsurround.gui.keyboard.KeyBindings;
import org.orecruncher.dsurround.lib.FrameworkUtils;
import org.orecruncher.dsurround.lib.TickCounter;
import org.orecruncher.dsurround.lib.logging.ModLog;
import org.orecruncher.dsurround.processing.Handlers;
import org.orecruncher.dsurround.runtime.diagnostics.ClientProfiler;
import org.orecruncher.dsurround.runtime.diagnostics.RuntimeDiagnostics;
import org.orecruncher.dsurround.runtime.diagnostics.SoundEngineDiagnostics;
import org.orecruncher.dsurround.sound.StartupSoundHandler;

import java.nio.file.Files;
import java.nio.file.Path;

@Environment(EnvType.CLIENT)
public class Client implements ClientModInitializer {

    public static final String ModId = "dsurround";
    public static final ModLog LOGGER = new ModLog(ModId);
    public static final String Branding = FrameworkUtils.getModBranding(ModId);

    /**
     * Basic configuration settings
     */
    public static final Configuration Config = Configuration.getConfig();

    /**
     * Settings for individual sound configuration
     */
    public static final SoundConfiguration SoundConfig = SoundConfiguration.getConfig();

    /**
     * Path to the mod's configuration directory
     */
    public static final Path CONFIG_PATH = FrameworkUtils.getConfigPath(ModId);

    /**
     * Path to the external config data cache for user customization
     */
    public static final Path DATA_PATH = Path.of(CONFIG_PATH.toString(), "configs");

    /**
     * Path to the external folder for dumping data
     */
    public static final Path DUMP_PATH = Path.of(CONFIG_PATH.toString(), "dumps");

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing...");

        createPath(CONFIG_PATH);
        createPath(DATA_PATH);
        createPath(DUMP_PATH);

        ClientLifecycleEvents.CLIENT_STARTED.register(this::onComplete);

        TickCounter.register();
        StartupSoundHandler.register();
        KeyBindings.register();
        Commands.register();

        // Register diagnostic handlers.  Ordering is semi important for
        // debug display layout.
        RuntimeDiagnostics.register();
        ClientProfiler.register();
        SoundEngineDiagnostics.register();

        LOGGER.info("Initialization complete");
    }

    private static void createPath(final Path path) {
        try {
            Files.createDirectories(path);
        } catch (final Throwable t) {
            LOGGER.error(t, "Unable to create data path %s", path.toString());
        }
    }

    public void onComplete(MinecraftClient client) {
        // Initialize our sounds
        SoundLibrary.load();
        BiomeLibrary.load();
        DimensionLibrary.load();
        Handlers.initialize();
    }
}
