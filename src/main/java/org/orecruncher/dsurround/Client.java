package org.orecruncher.dsurround;

import net.minecraft.client.Minecraft;
import org.orecruncher.dsurround.config.libraries.*;
import org.orecruncher.dsurround.config.libraries.impl.*;
import org.orecruncher.dsurround.effects.particles.ParticleSheets;
import org.orecruncher.dsurround.gui.overlay.OverlayManager;
import org.orecruncher.dsurround.gui.keyboard.KeyBindings;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.config.ConfigurationData;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.events.HandlerPriority;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.logging.ModLog;
import org.orecruncher.dsurround.eventing.ClientState;
import org.orecruncher.dsurround.lib.version.IVersionChecker;
import org.orecruncher.dsurround.lib.version.VersionChecker;
import org.orecruncher.dsurround.lib.version.VersionResult;
import org.orecruncher.dsurround.processing.Handlers;
import org.orecruncher.dsurround.runtime.ConditionEvaluator;
import org.orecruncher.dsurround.runtime.IConditionEvaluator;
import org.orecruncher.dsurround.sound.IAudioPlayer;
import org.orecruncher.dsurround.sound.MinecraftAudioPlayer;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class Client {

    /**
     * Basic configuration settings
     */
    public static Configuration Config;

    private final IModLog logger;
    private CompletableFuture<Optional<VersionResult>> versionInfo;

    public Client() {
        // Bootstrap library functions
        this.logger = Library.getLogger();
        Library.initialize(Constants.MOD_ID);
    }

    public void initializeClient() {
        this.logger.info("Client initializing...");

        // Hook the config load event so set we can set the debug flags on logging
        Configuration.CONFIG_CHANGED.register(cfg -> {
            if (cfg instanceof Configuration config) {
                if (this.logger instanceof ModLog ml) {
                    ml.setDebug(config.logging.enableDebugLogging);
                    ml.setTraceMask(config.logging.traceMask);
                }
            }
        });

        Handlers.registerHandlers();

        Config = ConfigurationData.getConfig(Configuration.class);

        ClientState.STARTED.register(this::onComplete, HandlerPriority.VERY_HIGH);
        ClientState.ON_CONNECT.register(this::onConnect, HandlerPriority.LOW);

        // Register core services
        ContainerManager.getRootContainer()
                .registerSingleton(Config)
                .registerSingleton(Config.soundSystem)
                .registerSingleton(Config.enhancedSounds)
                .registerSingleton(Config.thunderStorms)
                .registerSingleton(Config.blockEffects)
                .registerSingleton(Config.entityEffects)
                .registerSingleton(Config.footstepAccents)
                .registerSingleton(Config.particleTweaks)
                .registerSingleton(Config.compassAndClockOptions)
                .registerSingleton(Config.otherOptions)
                .registerSingleton(IConditionEvaluator.class, ConditionEvaluator.class)
                .registerSingleton(IVersionChecker.class, VersionChecker.class)
                .registerSingleton(ITagLibrary.class, TagLibrary.class)
                .registerSingleton(ISoundLibrary.class, SoundLibrary.class)
                .registerSingleton(IBiomeLibrary.class, BiomeLibrary.class)
                .registerSingleton(IDimensionLibrary.class, DimensionLibrary.class)
                .registerSingleton(IBlockLibrary.class, BlockLibrary.class)
                .registerSingleton(IItemLibrary.class, ItemLibrary.class)
                .registerSingleton(IEntityEffectLibrary.class, EntityEffectLibrary.class)
                .registerSingleton(IAudioPlayer.class, MinecraftAudioPlayer.class)
                .registerSingleton(OverlayManager.class);

        // Kick off version checking if configured.  This should run in parallel with initialization.
        if (Config.logging.enableModUpdateChatMessage)
            this.versionInfo = CompletableFuture.supplyAsync(ContainerManager.resolve(IVersionChecker.class)::getUpdateText);
        else
            this.versionInfo = CompletableFuture.completedFuture(Optional.empty());

        KeyBindings.register();
    }

    public void onComplete(Minecraft client) {

        this.logger.info("Finalizing initialization...");
        var container = ContainerManager.getRootContainer();

        // Register the Minecraft sound manager
        container.registerSingleton(GameUtils.getSoundManager());

        // Register and initialize our libraries. Handlers will be reloaded in priority order.
        // Leave normal to very low priority for other things in the mod that would need such
        // notification.
        AssetLibraryEvent.RELOAD.register(container.resolve(ISoundLibrary.class)::reload, HandlerPriority.VERY_HIGH);
        AssetLibraryEvent.RELOAD.register(container.resolve(ITagLibrary.class)::reload, HandlerPriority.VERY_HIGH);
        AssetLibraryEvent.RELOAD.register(container.resolve(IBiomeLibrary.class)::reload, HandlerPriority.HIGH);
        AssetLibraryEvent.RELOAD.register(container.resolve(IBlockLibrary.class)::reload, HandlerPriority.HIGH);
        AssetLibraryEvent.RELOAD.register(container.resolve(IItemLibrary.class)::reload, HandlerPriority.HIGH);
        AssetLibraryEvent.RELOAD.register(container.resolve(IEntityEffectLibrary.class)::reload, HandlerPriority.HIGH);
        AssetLibraryEvent.RELOAD.register(container.resolve(IDimensionLibrary.class)::reload, HandlerPriority.HIGH);

        ClientState.TAG_SYNC.register(event -> {
            this.logger.info("Tag sync event received - reloading libraries");
            AssetLibraryEvent.reload();
        }, HandlerPriority.VERY_HIGH);

        // Make the libraries load their data. Priority determines the sequence.
        AssetLibraryEvent.reload();

        // Force instantiation of the core Handler.  This should cause the rest
        // of the dependencies to be initialized.
        container.resolve(Handlers.class);

        // Make sure our particle sheets get registered otherwise they will not render.
        // These sheets are purely client side - they have to be manhandled into the
        // Minecraft environment.
        ParticleSheets.register();

        this.logger.info("Done!");
    }

    private void onConnect(Minecraft minecraftClient) {
        // Display version information when joining a game and when a chat window is available.
        try {
            var versionQueryResult = this.versionInfo.get();
            if (versionQueryResult.isPresent()) {
                var result = versionQueryResult.get();

                this.logger.info("Update to %s version %s is available", result.displayName(), result.version());
                var player = GameUtils.getPlayer();
                player.ifPresent(p -> p.sendSystemMessage(result.getChatText()));
            } else if(Config.logging.enableModUpdateChatMessage) {
                this.logger.info("The mod version is current");
            }
        } catch (Throwable t) {
            this.logger.error(t, "Unable to process version information");
        }
    }
}
