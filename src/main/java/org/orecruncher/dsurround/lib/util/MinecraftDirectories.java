package org.orecruncher.dsurround.lib.util;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

public final class MinecraftDirectories implements IMinecraftDirectories {

    @Override
    public Path getGameDirectory() {
        return FabricLoader.getInstance().getGameDir();
    }

    @Override
    public Path getConfigDirectory() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public Path getCrashReportDirectory() {
        return getGameDirectory().resolve("crash-reports");
    }
}
