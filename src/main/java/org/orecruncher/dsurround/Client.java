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
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.infra.ModInformation;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.events.HandlerPriority;
import org.orecruncher.dsurround.lib.infra.IMinecraftMod;
import org.orecruncher.dsurround.lib.infra.events.ClientState;
import org.orecruncher.dsurround.lib.logging.ModLog;
import org.orecruncher.dsurround.lib.scanner.Scanner;
import org.orecruncher.dsurround.lib.version.IVersionChecker;
import org.orecruncher.dsurround.lib.version.VersionChecker;
import org.orecruncher.dsurround.lib.version.VersionResult;
import org.orecruncher.dsurround.processing.Handlers;
import org.orecruncher.dsurround.runtime.diagnostics.*;
import org.orecruncher.dsurround.sound.IAudioPlayer;
import org.orecruncher.dsurround.sound.MinecraftAudioPlayer;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Environment(EnvType.CLIENT)
public class Client implements IMinecraftMod, ClientModInitializer {

    public static final String ModId = "dsurround";
    public static final ModLog LOGGER = new ModLog(ModId);
    /**
     * Basic configuration settings
     */
    public static Configuration Config;

    private ModInformation modInfo;
    private CompletableFuture<Optional<VersionResult>> versionInfo;

    @Override
    public String get_modId() {
        return ModId;
    }

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing...");

        // Hook the config load event so set we can set the debug flags on logging
        Configuration.CONFIG_CHANGED.register(event -> {
            if (event.config() instanceof Configuration config) {
                LOGGER.setDebug(config.logging.enableDebugLogging);
                LOGGER.setTraceMask(config.logging.traceMask);
            }
        });

        // Bootstrap library functions
        Library.initialize(this, LOGGER);

        Config = Configuration.getConfig();

        var container = ContainerManager.getDefaultContainer();
        this.modInfo = container.resolve(ModInformation.class);

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
                .registerSingleton(IVersionChecker.class, VersionChecker.class)
                .registerSingleton(ISoundLibrary.class, SoundLibrary.class)
                .registerSingleton(IBiomeLibrary.class, BiomeLibrary.class)
                .registerSingleton(IDimensionLibrary.class, DimensionLibrary.class)
                .registerSingleton(IBlockLibrary.class, BlockLibrary.class)
                .registerSingleton(IItemLibrary.class, ItemLibrary.class)
                .registerSingleton(IEntityEffectLibrary.class, EntityEffectLibrary.class)
                .registerSingleton(IAudioPlayer.class, MinecraftAudioPlayer.class);

        // Kick off version checking if configured.  This should run in parallel with initialization.
        if (Config.logging.enableModUpdateChatMessage)
            this.versionInfo = CompletableFuture.supplyAsync(ContainerManager.resolve(IVersionChecker.class)::getUpdateText);
        else
            this.versionInfo = CompletableFuture.completedFuture(Optional.empty());

        KeyBindings.register();

        LOGGER.info("Initialization complete");
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

        // Make sure our particle sheets get registered otherwise they will not render.
        // These sheets are purely client side - they have to be manhandled into the
        // Minecraft environment.
        ParticleSheets.register();
    }

    private void onConnect(MinecraftClient minecraftClient) {
        // Display version information when joining a game and when a chat window is available.
        try {
            var versionQueryResult = this.versionInfo.get();
            if (versionQueryResult.isPresent()) {
                var result = versionQueryResult.get();

                LOGGER.info("Update to %s version %s is available", result.displayName, result.version);
                var player = GameUtils.getPlayer();
                player.sendMessage(result.getChatText(), false);
            } else if(Config.logging.enableModUpdateChatMessage) {
                LOGGER.info("The mod version is current");
            }
        } catch (Throwable t) {
            LOGGER.error(t, "Unable to process version information");
        }
    }
}
