package org.orecruncher.dsurround.lib;

import com.google.common.base.Preconditions;
import net.minecraft.client.MinecraftClient;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.events.HandlerPriority;
import org.orecruncher.dsurround.lib.infra.IMinecraftMod;
import org.orecruncher.dsurround.lib.infra.ModInformation;
import org.orecruncher.dsurround.lib.infra.events.ClientState;
import org.orecruncher.dsurround.lib.infra.events.ClientWorldState;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.system.ISystemClock;
import org.orecruncher.dsurround.lib.system.ITickCount;
import org.orecruncher.dsurround.lib.system.SystemClock;
import org.orecruncher.dsurround.lib.system.TickCounter;
import org.orecruncher.dsurround.lib.threading.IClientTasking;
import org.orecruncher.dsurround.lib.threading.ClientTasking;
import org.orecruncher.dsurround.lib.util.IMinecraftDirectories;
import org.orecruncher.dsurround.lib.util.MinecraftDirectories;

import java.util.Optional;

/**
 * Logic used to initialize the library runtime
 */
public final class Library {

    private static IModLog _logger;

    /**
     * Instance that can be used by library components to obtain a reference to the active server.
     */
    @Nullable
    private static MinecraftClient _client;

    /**
     * Gets the active Minecraft server instance if available.
     */
    public static Optional<MinecraftClient> getMinecraftClient() {
        return Optional.ofNullable(_client);
    }

    /**
     * Initializes key functionality of library logic during startup.
     */
    public static void initialize(IMinecraftMod mod, IModLog logger) {
        Preconditions.checkNotNull(mod);
        Preconditions.checkNotNull(logger);

        _logger = logger;

        // Do this first so the rest of the library can get dependencies
        configureServiceDependencies(mod, logger);

        // Miscellaneous configuration
        configureStateHandlers();

        // Hook server lifecycle so logs get emitted
        ClientState.STARTED.register(Library::onClientStarting, HandlerPriority.VERY_HIGH);
        ClientState.STOPPING.register((ignore) -> logger.info("Client stopping"), HandlerPriority.VERY_HIGH);
    }

    public static IModLog getLogger() {
        return _logger;
    }

    public static IMinecraftMod getMinecraftMod() {
        return ContainerManager.resolve(IMinecraftMod.class);
    }

    public static IMinecraftDirectories getMinecraftDirectories() {
        return ContainerManager.resolve(IMinecraftDirectories.class);
    }

    public static IClientTasking getClientTasking() {
        return ContainerManager.resolve(IClientTasking.class);
    }

    private static void onClientStarting(MinecraftClient client) {
        _logger.info("Client starting");
        _client = client;
    }

    private static void configureServiceDependencies(IMinecraftMod mod, IModLog logger) {
        var modInfo = ModInformation.getModInformation(mod.get_modId());
        ContainerManager
                .getDefaultContainer()
                .registerSingleton(IModLog.class, logger)
                .registerSingleton(IMinecraftMod.class, mod)
                .registerSingleton(ModInformation.class, modInfo)
                .registerSingleton(ISystemClock.class, SystemClock.class)
                .registerSingleton(IMinecraftDirectories.class, MinecraftDirectories.class)
                .registerSingleton(IClientTasking.class, ClientTasking.class)
                .registerSingleton(ITickCount.class, TickCounter.class);
    }

    private static void configureStateHandlers() {
        ClientState.initialize();
        ClientWorldState.initialize();
    }
}
