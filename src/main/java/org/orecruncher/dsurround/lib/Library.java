package org.orecruncher.dsurround.lib;

import com.google.common.base.Preconditions;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.events.HandlerPriority;
import org.orecruncher.dsurround.lib.platform.IMinecraftMod;
import org.orecruncher.dsurround.lib.platform.ModInformation;
import org.orecruncher.dsurround.lib.platform.Services;
import org.orecruncher.dsurround.lib.platform.events.ClientState;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.system.ISystemClock;
import org.orecruncher.dsurround.lib.system.ITickCount;
import org.orecruncher.dsurround.lib.system.SystemClock;
import org.orecruncher.dsurround.lib.system.TickCounter;
import org.orecruncher.dsurround.lib.threading.IClientTasking;
import org.orecruncher.dsurround.lib.threading.ClientTasking;
import org.orecruncher.dsurround.lib.util.IMinecraftDirectories;
import org.orecruncher.dsurround.lib.util.MinecraftDirectories;

/**
 * Logic used to initialize the library runtime
 */
public final class Library {

    private static IModLog _logger;

    /**
     * Initializes key functionality of library logic during startup.
     */
    public static void initialize(@NotNull IMinecraftMod mod, @NotNull IModLog logger) {
        Preconditions.checkNotNull(mod);
        Preconditions.checkNotNull(logger);

        _logger = logger;

        // Do this first so the rest of the library can get dependencies
        configureServiceDependencies(mod, logger);

        // Initialize event handlers
        Services.EVENT_REGISTRATIONS.register();

        // Hook server lifecycle so logs get emitted
        ClientState.STARTED.register((ignore -> _logger.info("Client starting")), HandlerPriority.VERY_HIGH);
        ClientState.STOPPING.register(ignore -> _logger.info("Client stopping"), HandlerPriority.VERY_HIGH);
    }

    @NotNull
    public static IModLog getLogger() {
        return _logger;
    }

    private static void configureServiceDependencies(IMinecraftMod mod, IModLog logger) {
        var modInfo = Services.PLATFORM.getModInformation(mod.get_modId())
                .orElseThrow( () -> new RuntimeException("Unable to acquire mod information!"));

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
}
