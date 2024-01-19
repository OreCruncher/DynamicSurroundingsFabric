package org.orecruncher.dsurround.lib;

import com.google.common.base.Preconditions;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.events.HandlerPriority;
import org.orecruncher.dsurround.lib.logging.ModLog;
import org.orecruncher.dsurround.lib.platform.*;
import org.orecruncher.dsurround.eventing.ClientState;
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

    private static final IModLog LOGGER = new ModLog(Constants.MOD_ID);

    /**
     * Initializes key functionality of library logic during startup.
     */
    public static void initialize(String modId) {
        LOGGER.info("Library initializing");
        Preconditions.checkNotNull(modId);

        // Do this first so the rest of the library can get dependencies
        configureServiceDependencies(modId);

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
        return Services.PLATFORM;
    }

    private static void configureServiceDependencies(String modId) {
        var modInfo = Services.PLATFORM.getModInformation(modId)
                .orElseThrow(() -> {
                    LOGGER.warn("Unable to acquire mod information for %s!", modId);
                    return new RuntimeException("Unable to acquire mod information!");
                });

        ContainerManager
                .getRootContainer()
                .registerSingleton(IPlatform.class, Services.PLATFORM)
                .registerSingleton(IModLog.class, LOGGER)
                .registerSingleton(ModInformation.class, modInfo)
                .registerSingleton(ISystemClock.class, SystemClock.class)
                .registerSingleton(IMinecraftDirectories.class, MinecraftDirectories.class)
                .registerSingleton(IClientTasking.class, ClientTasking.class)
                .registerSingleton(ITickCount.class, TickCounter.class);
    }
}
