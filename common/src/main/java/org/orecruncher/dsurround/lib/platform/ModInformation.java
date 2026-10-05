package org.orecruncher.dsurround.lib.platform;

import dev.architectury.platform.Platform;
import net.minecraft.SharedConstants;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.version.SemanticVersion;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

public final class ModInformation implements IMinecraftDirectories {

    // The mod's links: kept in gradle.properties with its other details, and written into this file by the build
    static final String LINKS_RESOURCE = "/assets/dsurround/mod_links.properties";
    private static final Properties LINKS = loadLinks(ModInformation.class.getResourceAsStream(LINKS_RESOURCE));

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

    /**
     * Where the list of released versions is, for the update check; empty if the link is missing or isn't a URL.
     */
    public Optional<URL> getUpdateUrl() {
        return toUrl(link("update"));
    }

    public String curseForgeLink() {
        return link("curseforge");
    }

    public String modrinthLink() {
        return link("modrinth");
    }

    public String discussionsLink() {
        return link("discussions");
    }

    /**
     * One of the mod's links by name (see mod_links.properties), or an empty string if there is no such link.
     */
    static String link(String name) {
        return LINKS.getProperty(name, "");
    }

    static Optional<URL> toUrl(String link) {
        if (link.isEmpty())
            return Optional.empty();
        try {
            return Optional.of(URI.create(link).toURL());
        } catch (IllegalArgumentException | MalformedURLException e) {
            return Optional.empty();
        }
    }

    /**
     * Reads the links file. Missing or unreadable, there are no links (logged): the update check is then skipped,
     * rather than the mod failing to load.
     */
    static Properties loadLinks(@Nullable InputStream stream) {
        var links = new Properties();
        if (stream == null) {
            Library.LOGGER.warn("The mod's links (%s) are missing; the update check is turned off", LINKS_RESOURCE);
            return links;
        }
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            links.load(reader);
        } catch (IOException e) {
            Library.LOGGER.error(e, "Unable to read the mod's links (%s); the update check is turned off", LINKS_RESOURCE);
        }
        return links;
    }

    public String getBranding() {
        return String.format("%s %s-%s", this.displayName, SharedConstants.getCurrentVersion().name(), this.version);
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

    /**
     * The Minecraft version, or empty if it isn't a version that can be compared (e.g. a snapshot). A release such
     * as 1.21, with no patch number, is 1.21.0.
     */
    public static Optional<SemanticVersion> getMinecraftVersion() {
        var container = Platform.getMod("minecraft");
        if (container != null) {
            try {
                return Optional.of(SemanticVersion.parseMinecraft(container.getVersion()));
            } catch (Exception ignored) {
            }
        }
        return Optional.empty();
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
        try {
            // This will throw if it there isn't a mod with the ID loaded. This differs
            // from Fabric behavior getting mod information.
            var container = Platform.getMod(modId);

            if (container != null)
                return Optional.of(container.getName());

        } catch (Throwable ignore) {
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
