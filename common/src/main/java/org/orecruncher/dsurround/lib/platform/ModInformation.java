package org.orecruncher.dsurround.lib.platform;

import dev.architectury.platform.Platform;
import net.minecraft.SharedConstants;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.version.SemanticVersion;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public final class ModInformation implements IMinecraftDirectories {

    // TODO: Move into external resources?
    private static final URI modUpdate = URI.create("https://raw.githubusercontent.com/OreCruncher/DynamicSurroundingsFabric/main/versions.json");
    private static final String modCurseForge = "https://www.curseforge.com/minecraft/mc-mods/dynamic-surroundings-fabric-edition";
    private static final String modModrinth = "https://modrinth.com/mod/dynamicsurroundingsfabric";

    private final String modId;
    private final String displayName;
    private final SemanticVersion version;

    private final Path modConfigDirectory;
    private final Path modDataDirectory;
    private final Path modDumpDirectory;

    public ModInformation(String modId, String displayName, SemanticVersion version) {
        this.modId = modId;
        this.displayName = displayName;
        this.version = version;
        this.modConfigDirectory = getConfigPath(modId);
        this.modDataDirectory = this.modConfigDirectory.resolve("configs");
        this.modDumpDirectory = this.modConfigDirectory.resolve("dumps");

        createPath(this.modDataDirectory);
        createPath(this.modDumpDirectory);
    }

    public String modId() {
        return this.modId;
    }

    public String displayName() {
        return this.displayName;
    }

    public SemanticVersion version() {
        return this.version;
    }

    public Path getModConfigDirectory() {
        return this.modConfigDirectory;
    }

    public Path getModDataDirectory() {
        return this.modDataDirectory;
    }

    public Path getModDumpDirectory() {
        return this.modDumpDirectory;
    }

    public Optional<URL> getUpdateUrl() {
        try {
            return Optional.of(modUpdate.toURL());
        } catch (MalformedURLException ignored) {
        }
        return Optional.empty();
    }

    public String curseForgeLink() {
        return modCurseForge;
    }

    public String modrinthLink() {
        return modModrinth;
    }

    public String getBranding() {
        return String.format("%s %s-%s", this.displayName, SharedConstants.getCurrentVersion().getName(), this.version);
    }

    public static Optional<ModInformation> getModInformation(String modId) {
        return Platform.getOptionalMod(modId)
                .map(info -> {
                    try {
                        var displayName = info.getName();
                        var version = SemanticVersion.parse(info.getVersion());
                        var result = new ModInformation(modId, displayName, version);
                        return Optional.of(result);
                    } catch (Throwable t) {
                        return Optional.<ModInformation>empty();
                    }
                })
                .orElse(Optional.empty());
    }

    public static Optional<SemanticVersion> getMinecraftVersion() {
        return getModVersion("minecraft");
    }

    public static Optional<SemanticVersion> getModVersion(String namespace) {
        var container = Platform.getMod(namespace);
        if (container != null) {
            try {
                var version = container.getVersion();
                return Optional.of(SemanticVersion.parse(version));
            } catch (Exception ignored) {
            }
        }

        return Optional.empty();
    }

    public static Optional<String> getModDisplayName(String modId) {
        var container = Platform.getMod(modId);

        if (container != null) {
            return Optional.of(container.getName());
        }
        return Optional.empty();
    }

    public static Path getConfigPath(final String modId) {
        var configDir = Platform.getConfigFolder();
        var configPath = configDir.resolve(Objects.requireNonNull(modId));

        if (Files.notExists(configPath))
            try {
                Files.createDirectory(configPath);
            } catch (final IOException ex) {
                Library.LOGGER.error(ex, "Unable to create directory path %s", configPath.toString());
                configPath = configDir;
            }

        return configPath;
    }

    private static void createPath(final Path path) {
        try {
            Files.createDirectories(path);
        } catch (final Throwable t) {
            Library.LOGGER.error(t, "Unable to create data path %s", path.toString());
        }
    }
}
