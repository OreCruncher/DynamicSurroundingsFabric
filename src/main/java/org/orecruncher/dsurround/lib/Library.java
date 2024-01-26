package org.orecruncher.dsurround.lib;

import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.events.HandlerPriority;
import org.orecruncher.dsurround.lib.logging.ModLog;
import org.orecruncher.dsurround.lib.platform.*;
import org.orecruncher.dsurround.eventing.ClientState;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.seasons.ISeasonalInformation;
import org.orecruncher.dsurround.lib.seasons.SeasonManager;
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

    public static final String MOD_ID = Constants.MOD_ID;
    public static final IModLog LOGGER = new ModLog(MOD_ID);
    public static final IPlatform PLATFORM = Services.PLATFORM;

    /**
     * Initializes key functionality of library logic during startup.
     */
    public static void initialize() {
        LOGGER.info("Library initializing");

        // Do this first so the rest of the library can get dependencies
        configureServiceDependencies();

        // Hook server lifecycle so logs get emitted
        ClientState.STARTED.register((ignore -> LOGGER.info("Client starting")), HandlerPriority.VERY_HIGH);
        ClientState.STOPPING.register(ignore -> LOGGER.info("Client stopping"), HandlerPriority.VERY_HIGH);
    }

    private static void configureServiceDependencies() {
        var modInfo = PLATFORM.getModInformation(MOD_ID)
                .orElseThrow(() -> {
                    LOGGER.warn("Unable to acquire mod information for %s!", MOD_ID);
                    return new RuntimeException("Unable to acquire mod information!");
                });

        ContainerManager
                .getRootContainer()
                .registerSingleton(IPlatform.class, PLATFORM)
                .registerSingleton(IModLog.class, LOGGER)
                .registerSingleton(ModInformation.class, modInfo)
                .registerSingleton(ISystemClock.class, SystemClock.class)
                .registerSingleton(IMinecraftDirectories.class, MinecraftDirectories.class)
                .registerSingleton(IClientTasking.class, ClientTasking.class)
                .registerSingleton(ITickCount.class, TickCounter.class)
                .registerSingleton(ISeasonalInformation.class, SeasonManager.HANDLER);
    }
}
