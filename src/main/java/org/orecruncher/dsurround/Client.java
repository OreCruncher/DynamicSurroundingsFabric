package org.orecruncher.dsurround;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import org.orecruncher.dsurround.commands.Commands;
import org.orecruncher.dsurround.config.*;
import org.orecruncher.dsurround.gui.keyboard.KeyBindings;
import org.orecruncher.dsurround.lib.FrameworkUtils;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.TickCounter;
import org.orecruncher.dsurround.lib.logging.ModLog;
import org.orecruncher.dsurround.lib.version.VersionChecker;
import org.orecruncher.dsurround.processing.Handlers;
import org.orecruncher.dsurround.runtime.diagnostics.ClientProfiler;
import org.orecruncher.dsurround.runtime.diagnostics.RuntimeDiagnostics;
import org.orecruncher.dsurround.runtime.diagnostics.SoundEngineDiagnostics;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Environment(EnvType.CLIENT)
public class Client implements ClientModInitializer {

    public static final String ModId = "dsurround";
    public static final ModLog LOGGER = new ModLog(ModId);
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
    public static final String Branding = FrameworkUtils.getModBranding(ModId);
    /**
     * Basic configuration settings
     */
    public static final Configuration Config = Configuration.getConfig();
    /**
     * Settings for individual sound configuration
     */
    public static final SoundConfiguration SoundConfig = SoundConfiguration.getConfig();

    private FrameworkUtils.ModCustomData modInfo;
    private CompletableFuture<Optional<VersionChecker.VersionResult>> versionInfo;

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing...");

        // Get the custom metadata from the JAR
        var info = FrameworkUtils.getModCustomData(ModId, ModId);
        info.ifPresent(value -> this.modInfo = value);

        createPath(CONFIG_PATH);
        createPath(DATA_PATH);
        createPath(DUMP_PATH);

        ClientLifecycleEvents.CLIENT_STARTED.register(this::onComplete);
        ClientPlayConnectionEvents.JOIN.register(this::onConnect);

        TickCounter.register();
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

    public void refreshConfigs() {
        SoundLibrary.load();
        BiomeLibrary.load();
        DimensionLibrary.load();
        BlockLibrary.load();
        EntityEffectLibrary.load();
    }

    public void onComplete(MinecraftClient client) {
        // Initialize our sounds
        this.refreshConfigs();
        Handlers.initialize();

        // Kick off version checking.  This should run in parallel with initialization.
        this.versionInfo = CompletableFuture.supplyAsync(this::getVersionText);
    }

    private Optional<VersionChecker.VersionResult> getVersionText() {

        if (this.modInfo == null)
            return Optional.empty();

        var modContainer = FrameworkUtils.getModContainer(ModId);
        if (modContainer.isEmpty())
            return Optional.empty();

        var metadata = modContainer.get().getMetadata();

        var displayName = metadata.getName();
        var modVersion = metadata.getVersion();
        var minecraftVersion = FrameworkUtils.getModVersion("minecraft");

        URL updateURL = null;

        try {
            updateURL = new URL(this.modInfo.getString("updateURL"));
        } catch (Throwable t) {
            LOGGER.warn("Unable to parse update URL");
        }

        if (updateURL == null)
            return Optional.empty();

        return VersionChecker.getUpdateText(displayName, minecraftVersion, modVersion, updateURL);
    }

    private void onConnect(ClientPlayNetworkHandler clientPlayNetworkHandler, PacketSender packetSender, MinecraftClient minecraftClient) {
        // Display version information when joining a game and when a chat window is available.
        try {
            if (this.versionInfo != null) {
                var versionQueryResult = this.versionInfo.get();
                if (versionQueryResult.isPresent()) {
                    var result = versionQueryResult.get();

                    LOGGER.info("Update to %s v%s is available", result.displayName, result.version);

                    if (Config.logging.enableModUpdateChatMessage) {
                        var player = GameUtils.getPlayer();
                        player.sendMessage(versionQueryResult.get().getChatText(), false);
                    }
                }
            }
        } catch(Throwable t) {
            LOGGER.error(t, "Unable to process version information");
        }
    }
}
