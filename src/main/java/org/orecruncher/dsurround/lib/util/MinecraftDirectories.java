package org.orecruncher.dsurround.lib.util;

import net.fabricmc.loader.api.FabricLoader;
import org.orecruncher.dsurround.lib.FrameworkUtils;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.infra.ModInformation;

import java.nio.file.Files;
import java.nio.file.Path;

public final class MinecraftDirectories implements IMinecraftDirectories {

    private final Path gameDirectory;
    private final Path configDirectory;
    private final Path crashReportDirectory;
    private final Path modConfigDirectory;
    private final Path modDataDirectory;
    private final Path modDumpDirectory;

    public MinecraftDirectories(ModInformation modInfo) {
        this.gameDirectory = FabricLoader.getInstance().getGameDir();
        this.configDirectory = FabricLoader.getInstance().getConfigDir();
        this.crashReportDirectory = this.gameDirectory.resolve("crash-reports");
        this.modConfigDirectory = FrameworkUtils.getConfigPath(modInfo.get_modId());
        this.modDataDirectory = this.modConfigDirectory.resolve("configs");
        this.modDumpDirectory = this.modConfigDirectory.resolve("dumps");

        createPath(this.modConfigDirectory);
        createPath(this.modDataDirectory);
        createPath(this.modDumpDirectory);
    }

    @Override
    public Path getGameDirectory() {
        return this.gameDirectory;
    }

    @Override
    public Path getConfigDirectory() {
        return this.configDirectory;
    }

    @Override
    public Path getCrashReportDirectory() {
        return this.crashReportDirectory;
    }

    @Override
    public Path getModConfigDirectory() {
        return this.modConfigDirectory;
    }

    @Override
    public Path getModDataDirectory() {
        return this.modDataDirectory;
    }

    @Override
    public Path getModDumpDirectory() {
        return this.modDumpDirectory;
    }

    private static void createPath(final Path path) {
        try {
            Files.createDirectories(path);
        } catch (final Throwable t) {
            Library.getLogger().error(t, "Unable to create data path %s", path.toString());
        }
    }
}
