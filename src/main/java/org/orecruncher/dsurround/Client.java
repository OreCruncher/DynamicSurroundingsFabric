package org.orecruncher.dsurround;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.MinecraftClient;
import org.orecruncher.dsurround.config.Configuration;
import org.orecruncher.dsurround.config.SoundConfiguration;
import org.orecruncher.dsurround.eventing.ClientEventHooks;
import org.orecruncher.dsurround.gui.keyboard.KeyBindings;
import org.orecruncher.dsurround.lib.FrameworkUtils;
import org.orecruncher.dsurround.lib.TickCounter;
import org.orecruncher.dsurround.lib.logging.ModLog;
import org.orecruncher.dsurround.runtime.diagnostics.ClientProfiler;
import org.orecruncher.dsurround.runtime.diagnostics.RuntimeDiagnostics;
import org.orecruncher.dsurround.runtime.diagnostics.SoundEngineDiagnostics;
import org.orecruncher.dsurround.sound.SoundLibrary;
import org.orecruncher.dsurround.sound.StartupSoundHandler;

@Environment(EnvType.CLIENT)
public class Client implements ClientModInitializer {

    public static final String ModId = "dsurround";
    public static final ModLog LOGGER = new ModLog(ModId);
    public static final Configuration Config = Configuration.getConfig();
    public static final SoundConfiguration SoundConfig = SoundConfiguration.getConfig();
    public static final String Branding = FrameworkUtils.getModBranding(ModId);

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing...");

        ClientLifecycleEvents.CLIENT_STARTED.register(this::onComplete);

        TickCounter.register();
        StartupSoundHandler.register();
        KeyBindings.register();

        // Register diagnostic handlers.  Ordering is semi important for
        // debug display layout.
        RuntimeDiagnostics.register();
        ClientProfiler.register();
        SoundEngineDiagnostics.register();

        LOGGER.info("Initialization complete");
    }

    public void onComplete(MinecraftClient client) {
        // Initialize our sounds
        SoundLibrary.initialize();
    }
}
