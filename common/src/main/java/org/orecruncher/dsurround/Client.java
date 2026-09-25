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
import org.orecruncher.dsurround.lib.seasons.ISeasonalInformation;
import org.orecruncher.dsurround.lib.seasons.SeasonManager;
import org.orecruncher.dsurround.lib.version.IVersionChecker;
import org.orecruncher.dsurround.lib.version.VersionChecker;
import org.orecruncher.dsurround.lib.version.VersionResult;
import org.orecruncher.dsurround.processing.Handlers;
import org.orecruncher.dsurround.processing.fog.HolisticFogRangeCalculator;
import org.orecruncher.dsurround.runtime.ConditionEvaluator;
import org.orecruncher.dsurround.runtime.IConditionEvaluator;
import org.orecruncher.dsurround.runtime.oracle.IDimensionOracle;
import org.orecruncher.dsurround.runtime.oracle.ILevelOracle;
import org.orecruncher.dsurround.runtime.oracle.IMinecraftClock;
import org.orecruncher.dsurround.runtime.oracle.impl.DimensionOracle;
import org.orecruncher.dsurround.runtime.oracle.impl.LevelOracle;
import org.orecruncher.dsurround.runtime.oracle.impl.MinecraftClock;
import org.orecruncher.dsurround.sound.AudioPlayerDebug;
import org.orecruncher.dsurround.sound.IAudioPlayer;
import org.orecruncher.dsurround.sound.AudioPlayer;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class Client {

    /**
     * Basic configuration settings
     */
    public static Configuration Config;

    private static CompletableFuture<Optional<VersionResult>> versionInfo;

    public static void initialize() {
        // Bootstrap library functions
        Library.LOGGER.info("[%s] Bootstrapping", Constants.MOD_ID);

        ContainerManager.getRootContainer()
                .registerSingleton(IModLog.class, Library.LOGGER)
                .registerSingleton(IConfigScreenFactoryProvider.class, ClothAPIFactoryProvider.class);

        // Setup debug trace on the logger. It's not guaranteed that we
        // are the first getting the log file, so we can't rely
        // on the event hook.  (ModMenu can trigger this when it looks for
        // the hook in our mod before we had a chance to initialize.)
        Config = ConfigurationData.getConfig(Configuration.class);
        if (Library.LOGGER instanceof ModLog ml) {
            ml.setDebug(Config.logging.enableDebugLogging);
            ml.setTraceMask(Config.logging.traceMask);
        }

        // Hook the config load event so set we can set the debug flags when
        // the config changes.
        Configuration.CONFIG_CHANGED_EVENT.register(cfg -> {
            if (cfg instanceof Configuration config && Library.LOGGER instanceof ModLog ml) {
                ml.setDebug(config.logging.enableDebugLogging);
                ml.setTraceMask(config.logging.traceMask);
            }
        });

        Library.initialize();

        // Register the Minecraft sound manager using a factory. Avoids issue with ModernUI and their dinger.
        ContainerManager.getRootContainer()
                .registerFactory(SoundManager.class, GameUtils::getSoundManager);

        // Register configuration elements
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
                .registerSingleton(Config.musicManagerOptions)
                .registerSingleton(Config.otherOptions);

        Library.LOGGER.info("[%s] Boostrap completed", Constants.MOD_ID);
    }

    public static void initializeClient() {
        Library.LOGGER.info("[%s] Client initializing", Constants.MOD_ID);

        if (Client.Config.logging.registerCommands) {
            if (!Platform.isModLoaded(Constants.QUILTED_LOADER))
                ClientCommandRegistrationEvent.EVENT.register(Commands::register);
            else
                Library.LOGGER.info("Not registering client commands as mod is running in Quilt environment");
        }

        // Register the resource listener
        ReloadListenerRegistry.register(PackType.CLIENT_RESOURCES, new ReloadListener(), Constants.asId("reload_listener"));

        // Do the handlers
        Handlers.registerHandlers();

        ClientState.CLIENT_START_EVENT.register(Client::onComplete, HandlerPriority.VERY_HIGH);
        ClientState.CLIENT_CONNECT_EVENT.register(Client::onConnect, HandlerPriority.LOW);

        // Register core services
        ContainerManager.getRootContainer()
                .registerSingleton(IConditionEvaluator.class, ConditionEvaluator.class)
                .registerSingleton(IVersionChecker.class, VersionChecker.class)
                .registerSingleton(ITagLibrary.class, TagLibrary.class)
                .registerSingleton(ISoundLibrary.class, SoundLibrary.class)
                .registerSingleton(IBiomeLibrary.class, BiomeLibrary.class)
                .registerSingleton(IDimensionLibrary.class, DimensionLibrary.class)
                .registerSingleton(IDimensionOracle.class, DimensionOracle.class)
                .registerSingleton(ILevelOracle.class, LevelOracle.class)
                .registerSingleton(IMinecraftClock.class, MinecraftClock.class)
                // SeasonManager deferred as HANDLER is not initialized at this time
                .registerFactory(ISeasonalInformation.class, () -> SeasonManager.HANDLER)
                .registerSingleton(IBlockLibrary.class, BlockLibrary.class)
                .registerSingleton(IItemLibrary.class, ItemLibrary.class)
                .registerSingleton(IEntityEffectLibrary.class, EntityEffectLibrary.class)
                .registerSingleton(OverlayManager.class);

        // Depending on debug settings, enable the appropriate player
        if (Library.LOGGER.isDebugging())
            ContainerManager.getRootContainer().registerSingleton(IAudioPlayer.class, AudioPlayerDebug.class);
        else
            ContainerManager.getRootContainer().registerSingleton(IAudioPlayer.class, AudioPlayer.class);

        // Kick off version checking if configured.  This should run in parallel with initialization.
        if (Config.logging.enableModUpdateChatMessage) {
            versionInfo = CompletableFuture
                    .supplyAsync(ContainerManager.resolve(IVersionChecker.class)::getUpdateText)
                    .completeOnTimeout(Optional.empty(), 5, TimeUnit.SECONDS)
                    .exceptionally(t -> Optional.empty());
        } else {
            versionInfo = CompletableFuture.completedFuture(Optional.empty());
        }

        KeyBindings.register();

        Library.LOGGER.info("[%s] Client initialization complete", Constants.MOD_ID);
    }

    public static void onComplete(Minecraft client) {

        Library.LOGGER.info("[%s] Finalizing initialization", Constants.MOD_ID);
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

        ClientState.TAG_SYNC_EVENT.register(event -> {
            Library.LOGGER.info("Tag sync event received - reloading libraries");
            var resourceUtilities = ResourceUtilities.createForCurrentState();
            AssetLibraryEvent.RELOAD.invoker().onReload(resourceUtilities, IReloadEvent.Scope.TAGS);
        }, HandlerPriority.VERY_HIGH);

        // Add our fog handler
        container.registerSingleton(HolisticFogRangeCalculator.class);
        ContainerManager.resolve(HolisticFogRangeCalculator.class);

        // Force instantiation of the core Handler. This should cause the rest
        // of the dependencies to be initialized.
        container.resolve(Handlers.class);

        Library.LOGGER.info("[%s] Finalization complete", Constants.MOD_ID);
    }

    private static void onConnect(Minecraft minecraftClient) {
        // Display version information when joining a game and when a chat window is available.
        try {
            if (versionInfo == null || !versionInfo.isDone()) {
                Library.LOGGER.debug("Version check still pending; skipping join-time update notice");
                return;
            }

            var versionQueryResult = versionInfo.get();
            if (versionQueryResult.isPresent()) {
                var result = versionQueryResult.get();
                Library.LOGGER.info("Update to %s version %s is available", result.displayName(), result.version());
                var player = GameUtils.getPlayer();
                player.ifPresent(p -> p.sendSystemMessage(result.getChatText()));
            } else if(Config.logging.enableModUpdateChatMessage) {
                Library.LOGGER.info("The mod version is current");
            }
        } catch (Throwable t) {
            Library.LOGGER.error(t, "Unable to process version information");
        }
    }
}
