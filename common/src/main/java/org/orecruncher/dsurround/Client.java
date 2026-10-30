package org.orecruncher.dsurround;

import dev.architectury.event.events.client.ClientCommandRegistrationEvent;
import dev.architectury.platform.Platform;
import dev.architectury.registry.ReloadListenerRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.entity.LightningBoltRenderer;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import org.orecruncher.dsurround.commands.Commands;
import org.orecruncher.dsurround.config.libraries.*;
import org.orecruncher.dsurround.config.libraries.impl.*;
import org.orecruncher.dsurround.eventing.ClientState;
import org.orecruncher.dsurround.eventing.IClientConnect;
import org.orecruncher.dsurround.eventing.IClientStarted;
import org.orecruncher.dsurround.eventing.IConfigChangedEvent;
import org.orecruncher.dsurround.eventing.IReloadEvent;
import org.orecruncher.dsurround.eventing.ITagSync;
import org.orecruncher.dsurround.gui.overlay.OverlayManager;
import org.orecruncher.dsurround.gui.keyboard.KeyBindings;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.config.ConfigurationData;
import org.orecruncher.dsurround.config.PresetLoader;
import org.orecruncher.dsurround.lib.config.IConfigScreenFactoryProvider;
import org.orecruncher.dsurround.lib.config.compat.ClothAPIFactoryProvider;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.events.HandlerPriority;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.logging.ModLog;
import org.orecruncher.dsurround.lib.platform.FeatureConflict;
import org.orecruncher.dsurround.lib.platform.IMinecraftDirectories;
import org.orecruncher.dsurround.lib.registry.ReloadListener;
import org.orecruncher.dsurround.lib.resources.ResourceUtilities;
import org.orecruncher.dsurround.lib.seasons.ISeasonalInformation;
import org.orecruncher.dsurround.lib.seasons.SeasonManager;
import org.orecruncher.dsurround.lib.version.IVersionChecker;
import org.orecruncher.dsurround.lib.version.VersionCheckException;
import org.orecruncher.dsurround.lib.version.VersionChecker;
import org.orecruncher.dsurround.lib.version.VersionResult;
import org.orecruncher.dsurround.mixinutils.OptionalHook;
import org.orecruncher.dsurround.processing.Handlers;
import org.orecruncher.dsurround.profiling.ProfileFiles;
import org.orecruncher.dsurround.runtime.ConditionEvaluator;
import org.orecruncher.dsurround.runtime.IConditionEvaluator;
import org.orecruncher.dsurround.runtime.oracle.IDimensionOracle;
import org.orecruncher.dsurround.runtime.oracle.ILevelOracle;
import org.orecruncher.dsurround.runtime.oracle.IMinecraftClock;
import org.orecruncher.dsurround.runtime.oracle.impl.DimensionOracle;
import org.orecruncher.dsurround.runtime.oracle.impl.LevelOracle;
import org.orecruncher.dsurround.runtime.oracle.impl.MinecraftClock;
import org.orecruncher.dsurround.runtime.weather.IWeatherSimulation;
import org.orecruncher.dsurround.runtime.weather.WeatherSimulation;
import org.orecruncher.dsurround.runtime.weather.WindService;
import org.orecruncher.dsurround.sound.AudioPlayerDebug;
import org.orecruncher.dsurround.sound.IAudioPlayer;
import org.orecruncher.dsurround.sound.AudioPlayer;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
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
        ModLog.setDebug(Config.diagnostics.enableDebugLogging);
        ModLog.setTraceMask(Config.diagnostics.traceMask);

        // Hook the config load event so set we can set the debug flags when
        // the config changes.
        IConfigChangedEvent.EVENT.register(cfg -> {
            if (cfg instanceof Configuration config) {
                ModLog.setDebug(config.diagnostics.enableDebugLogging);
                ModLog.setTraceMask(config.diagnostics.traceMask);
            }
        });

        // Connect the platform's client events to the mod's. Must happen before the client starts.
        ClientState.initialize();

        Library.initialize();

        // Register the Minecraft sound manager using a factory. Avoids issue with ModernUI and their dinger.
        ContainerManager.getRootContainer()
                .registerFactory(SoundManager.class, GameUtils::getSoundManager);

        // Register the configuration, and each of its groups of settings so a class can be given just the group it
        // needs. The groups are found from the configuration, so a new one is registered without being listed here.
        Config.registerWith(ContainerManager.getRootContainer());

        Library.LOGGER.info("[%s] Bootstrap completed", Constants.MOD_ID);
    }

    public static void initializeClient() {
        Library.LOGGER.info("[%s] Client initializing", Constants.MOD_ID);

        FeatureConflict.logBlocked(Library.LOGGER);

        if (Client.Config.generalOptions.registerCommands && !FeatureConflict.CLIENT_COMMANDS.isBlocked())
            ClientCommandRegistrationEvent.EVENT.register(Commands::register);

        // Register the resource listener
        ReloadListenerRegistry.register(PackType.CLIENT_RESOURCES, new ReloadListener(), Constants.asId("reload_listener"));

        // Do the handlers
        Handlers.registerHandlers();

        IClientStarted.EVENT.register(Client::onComplete, HandlerPriority.VERY_HIGH);
        IClientConnect.EVENT.register(Client::onConnect, HandlerPriority.LOW);

        // Register core services
        ContainerManager.getRootContainer()
                .registerSingleton(IConditionEvaluator.class, ConditionEvaluator.class)
                .registerSingleton(IVersionChecker.class, VersionChecker.class)
                .registerSingleton(ITagLibrary.class, TagLibrary.class)
                .registerSingleton(ISoundLibrary.class, SoundLibrary.class)
                .registerSingleton(IBiomeLibrary.class, BiomeLibrary.class)
                .registerSingleton(IDimensionLibrary.class, DimensionLibrary.class)
                .registerSingleton(PresetLoader.class)
                .registerSingleton(IDimensionOracle.class, DimensionOracle.class)
                .registerSingleton(ILevelOracle.class, LevelOracle.class)
                .registerSingleton(IMinecraftClock.class, MinecraftClock.class)
                // SeasonManager deferred as HANDLER is not initialized at this time
                .registerFactory(ISeasonalInformation.class, () -> SeasonManager.HANDLER)
                .registerSingleton(IBlockLibrary.class, BlockLibrary.class)
                .registerSingleton(IItemLibrary.class, ItemLibrary.class)
                .registerSingleton(IEntityEffectLibrary.class, EntityEffectLibrary.class)
                .registerSingleton(IWeatherSimulation.class, WeatherSimulation.class)
                .registerSingleton(WindService.class)
                .registerSingleton(OverlayManager.class);

        // Depending on debug settings, enable the appropriate player
        if (Library.LOGGER.isDebugging())
            ContainerManager.getRootContainer().registerSingleton(IAudioPlayer.class, AudioPlayerDebug.class);
        else
            ContainerManager.getRootContainer().registerSingleton(IAudioPlayer.class, AudioPlayer.class);

        // Kick off version checking if configured.  This should run in parallel with initialization.
        if (Config.generalOptions.enableModUpdateChatMessage) {
            // A failure or timeout completes the future exceptionally, so it isn't mistaken for "no recommendation"
            versionInfo = CompletableFuture
                    .supplyAsync(ContainerManager.resolve(IVersionChecker.class)::getVersionResult)
                    .orTimeout(5, TimeUnit.SECONDS);
        } else {
            versionInfo = CompletableFuture.completedFuture(Optional.empty());
        }

        KeyBindings.register();

        Library.LOGGER.info("[%s] Client initialization complete", Constants.MOD_ID);
    }

    /**
     * Last to run on a library reload: when debug logging is on, tells the player the reload happened.
     */
    private static void afterReload(ResourceUtilities resourceUtilities, IReloadEvent.Scope scope) {
        if (Config.diagnostics.enableDebugLogging) {
            var msg = Component.translatable("dsurround.text.reloadassets", Component.translatable("dsurround.modname"));
            GameUtils.getPlayer().ifPresent(p -> p.sendSystemMessage(msg));
        }
    }

    public static void onComplete(Minecraft client) {

        Library.LOGGER.info("[%s] Finalizing initialization", Constants.MOD_ID);
        var container = ContainerManager.getRootContainer();

        // Register and initialize our libraries. Handlers will be reloaded in priority order.
        // Leave normal to very low priority for other things in the mod that would need such
        // notification.
        IReloadEvent.EVENT.register(container.resolve(ISoundLibrary.class)::reload, HandlerPriority.VERY_HIGH);
        IReloadEvent.EVENT.register(container.resolve(ITagLibrary.class)::reload, HandlerPriority.VERY_HIGH);
        IReloadEvent.EVENT.register(container.resolve(IBiomeLibrary.class)::reload, HandlerPriority.HIGH);
        IReloadEvent.EVENT.register(container.resolve(IBlockLibrary.class)::reload, HandlerPriority.HIGH);
        IReloadEvent.EVENT.register(container.resolve(IItemLibrary.class)::reload, HandlerPriority.HIGH);
        IReloadEvent.EVENT.register(container.resolve(IEntityEffectLibrary.class)::reload, HandlerPriority.HIGH);
        IReloadEvent.EVENT.register(container.resolve(IDimensionLibrary.class)::reload, HandlerPriority.HIGH);
        IReloadEvent.EVENT.register(container.resolve(PresetLoader.class)::reload, HandlerPriority.HIGH);
        IReloadEvent.EVENT.register(Client::afterReload, HandlerPriority.VERY_LOW);

        ITagSync.EVENT.register(event -> {
            Library.LOGGER.info("Tag sync event received - reloading libraries");
            var resourceUtilities = ResourceUtilities.createForCurrentState();
            IReloadEvent.EVENT.invoker().onReload(resourceUtilities, IReloadEvent.Scope.TAGS);
        }, HandlerPriority.VERY_HIGH);

        // Registration is complete. Report circular or missing dependencies now, with the full list, rather than
        // one at a time as each is first used.
        var problems = container.validate(Handlers.class);
        if (problems.isEmpty()) {
            Library.LOGGER.info("Dependency registration validated: no problems found");
        } else {
            for (var problem : problems)
                Library.LOGGER.warn("Dependency registration: %s", problem);
        }

        // Force instantiation of the core Handler. This should cause the rest
        // of the dependencies to be initialized.
        container.resolve(Handlers.class);

        // Only the newest profile recordings are kept (see /dsprofile)
        ProfileFiles.prune(container.resolve(IMinecraftDirectories.class).getModProfileDirectory(), ProfileFiles.KEPT, Library.LOGGER);

        checkOptionalHooks();

        Library.LOGGER.info("[%s] Finalization complete", Constants.MOD_ID);
    }

    /**
     * Logs the optional game hooks that didn't apply because another mod changed the code they hook (in development,
     * stops the launch). The classes they are in are loaded first, so their mixins have been applied: naming a class
     * loads it.
     */
    private static void checkOptionalHooks() {
        var hooked = List.of(ClientLevel.class, LevelRenderer.class, WeatherEffectRenderer.class, SkyRenderer.class, FogRenderer.class,
                AtmosphericFogEnvironment.class, ParticleEngine.class, Particle.class, LightningBoltRenderer.class);
        Library.LOGGER.debug("Checking the optional hooks in %d classes", hooked.size());
        OptionalHook.check(Library.LOGGER, Platform.isDevelopmentEnvironment());
    }

    private static void onConnect(Minecraft minecraftClient) {
        // Display version information when joining a game and when a chat window is available.
        try {
            if (versionInfo == null || !versionInfo.isDone()) {
                Library.LOGGER.debug("Version check still pending; skipping join-time update notice");
                return;
            }

            Optional<VersionResult> versionQueryResult;
            try {
                versionQueryResult = versionInfo.join();
            } catch (CompletionException | CancellationException e) {
                Library.LOGGER.warn("Unable to check for an update: %s", VersionCheckException.describe(e));
                return;
            }

            if (versionQueryResult.isPresent()) {
                var result = versionQueryResult.get();
                if (result.updateAvailable()) {
                    Library.LOGGER.info("Update to %s version %s is available", result.displayName(), result.version());
                    var player = GameUtils.getPlayer();
                    player.ifPresent(p -> p.sendSystemMessage(result.getChatText()));
                } else {
                    Library.LOGGER.info("%s is current", result.displayName());
                }
            } else if (Config.generalOptions.enableModUpdateChatMessage) {
                Library.LOGGER.info("No recommended version is published for this version of Minecraft");
            }
        } catch (Throwable t) {
            Library.LOGGER.error(t, "Unable to process version information");
        }
    }
}
