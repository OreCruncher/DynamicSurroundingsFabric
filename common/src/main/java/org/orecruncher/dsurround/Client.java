package org.orecruncher.dsurround;

import dev.architectury.event.events.client.ClientCommandRegistrationEvent;
import dev.architectury.platform.Platform;
import dev.architectury.registry.ReloadListenerRegistry;
import net.minecraft.client.Minecraft;
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
import org.orecruncher.dsurround.lib.config.IConfigScreenFactoryProvider;
import org.orecruncher.dsurround.lib.config.compat.ClothAPIFactoryProvider;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.events.HandlerPriority;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.logging.ModLog;
import org.orecruncher.dsurround.lib.registry.ReloadListener;
import org.orecruncher.dsurround.lib.resources.ResourceUtilities;
import org.orecruncher.dsurround.lib.seasons.ISeasonalInformation;
import org.orecruncher.dsurround.lib.seasons.SeasonManager;
import org.orecruncher.dsurround.lib.version.IVersionChecker;
import org.orecruncher.dsurround.lib.version.VersionCheckException;
import org.orecruncher.dsurround.lib.version.VersionChecker;
import org.orecruncher.dsurround.lib.version.VersionResult;
import org.orecruncher.dsurround.processing.Handlers;
import org.orecruncher.dsurround.runtime.ConditionEvaluator;
import org.orecruncher.dsurround.runtime.IConditionEvaluator;
import org.orecruncher.dsurround.sound.AudioPlayerDebug;
import org.orecruncher.dsurround.sound.IAudioPlayer;
import org.orecruncher.dsurround.sound.AudioPlayer;

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
        ModLog.setDebug(Config.logging.enableDebugLogging);
        ModLog.setTraceMask(Config.logging.traceMask);

        // Hook the config load event so set we can set the debug flags when
        // the config changes.
        IConfigChangedEvent.EVENT.register(cfg -> {
            if (cfg instanceof Configuration config) {
                ModLog.setDebug(config.logging.enableDebugLogging);
                ModLog.setTraceMask(config.logging.traceMask);
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
                .registerSingleton(IDimensionInformation.class, DimensionInformation.class)
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
        if (Config.logging.enableDebugLogging) {
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

        Library.LOGGER.info("[%s] Finalization complete", Constants.MOD_ID);
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
            } else if (Config.logging.enableModUpdateChatMessage) {
                Library.LOGGER.info("No recommended version is published for this version of Minecraft");
            }
        } catch (Throwable t) {
            Library.LOGGER.error(t, "Unable to process version information");
        }
    }
}
