package org.orecruncher.dsurround;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import org.orecruncher.dsurround.commands.Commands;
import org.orecruncher.dsurround.config.*;
import org.orecruncher.dsurround.config.libraries.*;
import org.orecruncher.dsurround.config.libraries.impl.*;
import org.orecruncher.dsurround.effects.particles.ParticleSheets;
import org.orecruncher.dsurround.gui.keyboard.KeyBindings;
import org.orecruncher.dsurround.lib.FrameworkUtils;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.infra.ModInformation;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.events.HandlerPriority;
import org.orecruncher.dsurround.lib.infra.IMinecraftMod;
import org.orecruncher.dsurround.lib.infra.events.ClientState;
import org.orecruncher.dsurround.lib.logging.ModLog;
import org.orecruncher.dsurround.lib.scanner.Scanner;
import org.orecruncher.dsurround.lib.version.VersionChecker;
import org.orecruncher.dsurround.processing.Handlers;
import org.orecruncher.dsurround.runtime.diagnostics.*;
import org.orecruncher.dsurround.sound.IAudioPlayer;
import org.orecruncher.dsurround.sound.MinecraftAudioPlayer;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Environment(EnvType.CLIENT)
public class Client implements IMinecraftMod, ClientModInitializer {

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
    /**
     * Basic configuration settings
     */
    public static final Configuration Config = Configuration.getConfig();

    private ModInformation modInfo;
    private CompletableFuture<Optional<VersionChecker.VersionResult>> versionInfo;

    @Override
    public String get_modId() {
        return ModId;
    }

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing...");

        // Bootstrap library functions
        Library.initialize(this, LOGGER);

        var container = ContainerManager.getDefaultContainer();
        this.modInfo = container.resolve(ModInformation.class);

        // Kick off version checking.  This should run in parallel with initialization.
        this.versionInfo = CompletableFuture.supplyAsync(this::getVersionText);

        createPath(CONFIG_PATH);
        createPath(DATA_PATH);
        createPath(DUMP_PATH);

        ClientState.STARTED.register(this::onComplete, HandlerPriority.VERY_HIGH);
        ClientState.ON_CONNECT.register(this::onConnect, HandlerPriority.LOW);
        ClientState.TAG_SYNC.register(event -> {
            LOGGER.info("Tag sync event received - reloading libraries");
            AssetLibraryEvent.reload();
        }, HandlerPriority.VERY_HIGH);

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> Commands.register(dispatcher));

        // Register services
        container
            .registerSingleton(Config)
            .registerSingleton(Handlers.class)
            .registerSingleton(Scanner.class)
            .registerSingleton(Diagnostics.class)
            .registerSingleton(ISoundLibrary.class, SoundLibrary.class)
            .registerSingleton(IBiomeLibrary.class, BiomeLibrary.class)
            .registerSingleton(IDimensionLibrary.class, DimensionLibrary.class)
            .registerSingleton(IBlockLibrary.class, BlockLibrary.class)
            .registerSingleton(IItemLibrary.class, ItemLibrary.class)
            .registerSingleton(IEntityEffectLibrary.class, EntityEffectLibrary.class)
            .registerSingleton(IAudioPlayer.class, MinecraftAudioPlayer.class);

        KeyBindings.register();

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
        var container = ContainerManager.getDefaultContainer();

        // Register the Minecraft sound manager
        container.registerSingleton(GameUtils.getSoundManager());

        // Register and initialize our libraries.  This will cause the libraries
        // to be instantiated.
        AssetLibraryEvent.RELOAD.register(container.resolve(ISoundLibrary.class)::reload);
        AssetLibraryEvent.RELOAD.register(container.resolve(IBiomeLibrary.class)::reload);
        AssetLibraryEvent.RELOAD.register(container.resolve(DimensionLibrary.class)::reload);
        AssetLibraryEvent.RELOAD.register(container.resolve(IBlockLibrary.class)::reload);
        AssetLibraryEvent.RELOAD.register(container.resolve(IItemLibrary.class)::reload);
        AssetLibraryEvent.RELOAD.register(container.resolve(IEntityEffectLibrary.class)::reload);

        // Make the libraries load their data
        AssetLibraryEvent.reload();

        // Force instantiation of the core Handler.  This should cause the rest
        // of the dependencies to be initialized.
        var handlers = container.resolve(Handlers.class);

        // Make sure our particle sheets get registered so they can render
        ParticleSheets.register();
    }

    private Optional<VersionChecker.VersionResult> getVersionText() {

        if (this.modInfo == null)
            return Optional.empty();

        var displayName = this.modInfo.get_displayName();
        var modVersion = this.modInfo.get_version();
        var minecraftVersion = FrameworkUtils.getMinecraftVersion();

        URL updateURL = null;

        try {
            updateURL = this.modInfo.get_updateUrl();
        } catch (Throwable t) {
            LOGGER.warn("Unable to parse update URL");
        }

        if (updateURL == null)
            return Optional.empty();

        return VersionChecker.getUpdateText(displayName, minecraftVersion, modVersion, updateURL);
    }

    private void onConnect(MinecraftClient minecraftClient) {
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
