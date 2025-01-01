package org.orecruncher.dsurround;

import dev.architectury.event.events.client.ClientCommandRegistrationEvent;
import dev.architectury.platform.Platform;
import dev.architectury.registry.ReloadListenerRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.server.packs.PackType;
import org.orecruncher.dsurround.commands.Commands;
import org.orecruncher.dsurround.config.libraries.*;
import org.orecruncher.dsurround.config.libraries.impl.*;
import org.orecruncher.dsurround.effects.particles.Particles;
import org.orecruncher.dsurround.gui.overlay.OverlayManager;
import org.orecruncher.dsurround.gui.keyboard.KeyBindings;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.config.ConfigurationData;
import org.orecruncher.dsurround.lib.config.IConfigScreenFactoryProvider;
import org.orecruncher.dsurround.lib.config.compat.ClothAPIFactoryProvider;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.events.HandlerPriority;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.logging.ModLog;
import org.orecruncher.dsurround.eventing.ClientState;
import org.orecruncher.dsurround.lib.registry.ReloadListener;
import org.orecruncher.dsurround.lib.resources.ResourceUtilities;
import org.orecruncher.dsurround.lib.version.IVersionChecker;
import org.orecruncher.dsurround.lib.version.VersionChecker;
import org.orecruncher.dsurround.lib.version.VersionResult;
import org.orecruncher.dsurround.processing.Handlers;
import org.orecruncher.dsurround.processing.fog.HolisticFogRangeCalculator;
import org.orecruncher.dsurround.runtime.ConditionEvaluator;
import org.orecruncher.dsurround.runtime.IConditionEvaluator;
import org.orecruncher.dsurround.sound.AudioPlayerDebug;
import org.orecruncher.dsurround.sound.IAudioPlayer;
import org.orecruncher.dsurround.sound.AudioPlayer;

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
        this.logger = Library.LOGGER;

        ContainerManager.getRootContainer()
                .registerSingleton(IModLog.class, this.logger)
                .registerSingleton(IConfigScreenFactoryProvider.class, ClothAPIFactoryProvider.class);

        // Setup debug trace on the logger. It's not guaranteed that we
        // are the first getting the log file, so we can't rely
        // on the event hook.  (ModMenu can trigger this when it looks for
        // the hook in our mod before we had a chance to initialize.)
        Config = ConfigurationData.getConfig(Configuration.class);
        if (this.logger instanceof ModLog ml) {
            ml.setDebug(Config.logging.enableDebugLogging);
            ml.setTraceMask(Config.logging.traceMask);
        }

        // Hook the config load event so set we can set the debug flags when
        // the config changes.
        Configuration.CONFIG_CHANGED.register(cfg -> {
            if (cfg instanceof Configuration config) {
                if (this.logger instanceof ModLog ml) {
                    ml.setDebug(config.logging.enableDebugLogging);
                    ml.setTraceMask(config.logging.traceMask);
                }
            }
        });

        // Make sure our particle sheets get registered otherwise they will not render.
        // These sheets are purely client side - they have to be manhandled into the
        // Minecraft environment.
        Particles.register();
    }

    public void construct() {
        this.logger.info("[%s] Bootstrapping", Constants.MOD_ID);

        Library.initialize();

        // Register the Minecraft sound manager using a factory. Avoids issue with ModernUI and their dinger.
        ContainerManager.getRootContainer().registerFactory(SoundManager.class, GameUtils::getSoundManager);

        this.logger.info("[%s] Boostrap completed", Constants.MOD_ID);
    }

    public void initializeClient() {
        this.logger.info("[%s] Client initializing", Constants.MOD_ID);

        if (Client.Config.logging.registerCommands) {
            if (!Platform.isModLoaded(Constants.QUILTED_LOADER))
                ClientCommandRegistrationEvent.EVENT.register(Commands::register);
            else
                Library.LOGGER.info("Not registering client commands as mod is running in Quilt environment");
        }

        // Register the resource listener
        ReloadListenerRegistry.register(PackType.CLIENT_RESOURCES, new ReloadListener());

        // Do the handlers
        Handlers.registerHandlers();

        ClientState.STARTED.register(this::onComplete, HandlerPriority.VERY_HIGH);
        ClientState.ON_CONNECT.register(this::onConnect, HandlerPriority.LOW);

        // Register core services
        ContainerManager.getRootContainer()
                .registerSingleton(Config)
                .registerSingleton(Config.logging)
                .registerSingleton(Config.soundSystem)
                .registerSingleton(Config.enhancedSounds)
                .registerSingleton(Config.soundOptions)
                .registerSingleton(Config.blockEffects)
                .registerSingleton(Config.entityEffects)
                .registerSingleton(Config.footstepAccents)
                .registerSingleton(Config.particleTweaks)
                .registerSingleton(Config.compassAndClockOptions)
                .registerSingleton(Config.fogOptions)
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
                .registerSingleton(OverlayManager.class);

        // Depending on debug settings, enable the appropriate player
        if (this.logger.isDebugging())
            ContainerManager.getRootContainer().registerSingleton(IAudioPlayer.class, AudioPlayerDebug.class);
        else
            ContainerManager.getRootContainer().registerSingleton(IAudioPlayer.class, AudioPlayer.class);

        // Kick off version checking if configured.  This should run in parallel with initialization.
        if (Config.logging.enableModUpdateChatMessage)
            this.versionInfo = CompletableFuture.supplyAsync(ContainerManager.resolve(IVersionChecker.class)::getUpdateText);
        else
            this.versionInfo = CompletableFuture.completedFuture(Optional.empty());

        KeyBindings.register();

        this.logger.info("[%s] Client initialization complete", Constants.MOD_ID);
    }

    public void onComplete(Minecraft client) {

        this.logger.info("[%s] Completing initialization", Constants.MOD_ID);
        var container = ContainerManager.getRootContainer();

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
            var resourceUtilities = ResourceUtilities.createForCurrentState();
            AssetLibraryEvent.RELOAD.raise().onReload(resourceUtilities, IReloadEvent.Scope.TAGS);
        }, HandlerPriority.VERY_HIGH);

        // Add our fog handler
        container.registerSingleton(HolisticFogRangeCalculator.class);
        ContainerManager.resolve(HolisticFogRangeCalculator.class);

        // Force instantiation of the core Handler. This should cause the rest
        // of the dependencies to be initialized.
        container.resolve(Handlers.class);

        this.logger.info("[%s] Finalization complete", Constants.MOD_ID);
    }

    private void onConnect(Minecraft minecraftClient) {
        // Display version information when joining a game and when a chat window is available.
        try {
            var versionQueryResult = this.versionInfo.get();
            if (versionQueryResult.isPresent()) {
                var result = versionQueryResult.get();
                this.logger.info("Update to %s version %s is available", result.displayName(), result.version());
                var player = GameUtils.getPlayer();
                player.ifPresent(p -> p.displayClientMessage(result.getChatText(), false));
            } else if(Config.logging.enableModUpdateChatMessage) {
                this.logger.info("The mod version is current");
            }
        } catch (Throwable t) {
            this.logger.error(t, "Unable to process version information");
        }
    }
}
