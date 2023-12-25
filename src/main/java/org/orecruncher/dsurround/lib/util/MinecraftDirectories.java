package org.orecruncher.dsurround.lib.util;

import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.platform.IPlatform;
import org.orecruncher.dsurround.lib.platform.ModInformation;

import java.nio.file.Files;
import java.nio.file.Path;

public final class MinecraftDirectories implements IMinecraftDirectories {

    private final Path modConfigDirectory;
    private final Path modDataDirectory;
    private final Path modDumpDirectory;

    public MinecraftDirectories(ModInformation modInfo, IPlatform platform) {
        this.modConfigDirectory = platform.getConfigPath(modInfo.modId());
        this.modDataDirectory = this.modConfigDirectory.resolve("configs");
        this.modDumpDirectory = this.modConfigDirectory.resolve("dumps");

        createPath(this.modConfigDirectory);
        createPath(this.modDataDirectory);
        createPath(this.modDumpDirectory);
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
