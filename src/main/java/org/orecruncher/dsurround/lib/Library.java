package org.orecruncher.dsurround.lib;

import com.google.common.base.Preconditions;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.events.HandlerPriority;
import org.orecruncher.dsurround.lib.platform.*;
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

    // Loader-specific API implementations
    private static final IPlatform PLATFORM = ContainerManager.resolve(IPlatform.class);
    private static final IClientEventRegistrations EVENT_REGISTRATIONS = ContainerManager.resolve(IClientEventRegistrations.class);

    private static IModLog LOGGER;

    /**
     * Initializes key functionality of library logic during startup.
     */
    public static void initialize(@NotNull IMinecraftMod mod, @NotNull IModLog logger) {
        Preconditions.checkNotNull(mod);
        Preconditions.checkNotNull(logger);

        LOGGER = logger;

        // Do this first so the rest of the library can get dependencies
        configureServiceDependencies(mod, logger);

        // Initialize event handlers
        EVENT_REGISTRATIONS.register();

        // Hook server lifecycle so logs get emitted
        ClientState.STARTED.register((ignore -> LOGGER.info("Client starting")), HandlerPriority.VERY_HIGH);
        ClientState.STOPPING.register(ignore -> LOGGER.info("Client stopping"), HandlerPriority.VERY_HIGH);
    }

    @NotNull
    public static IModLog getLogger() {
        return LOGGER;
    }

    @NotNull
    public static IPlatform getPlatform() {
        return PLATFORM;
    }

    @NotNull
    public static IClientEventRegistrations getEventRegistrations() {
        return EVENT_REGISTRATIONS;
    }

    private static void configureServiceDependencies(IMinecraftMod mod, IModLog logger) {
        var modInfo = PLATFORM.getModInformation(mod.getModId())
                .orElseThrow( () -> new RuntimeException("Unable to acquire mod information!"));

        ContainerManager
                .getRootContainer()
                .registerSingleton(IModLog.class, logger)
                .registerSingleton(IMinecraftMod.class, mod)
                .registerSingleton(ModInformation.class, modInfo)
                .registerSingleton(ISystemClock.class, SystemClock.class)
                .registerSingleton(IMinecraftDirectories.class, MinecraftDirectories.class)
                .registerSingleton(IClientTasking.class, ClientTasking.class)
                .registerSingleton(ITickCount.class, TickCounter.class);
    }
}
