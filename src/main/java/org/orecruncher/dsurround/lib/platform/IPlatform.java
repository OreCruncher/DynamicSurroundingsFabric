package org.orecruncher.dsurround.lib.platform;

import net.minecraft.client.KeyMapping;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.config.ConfigurationData;
import org.orecruncher.dsurround.lib.config.IScreenFactory;
import org.orecruncher.dsurround.lib.version.SemanticVersion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public interface IPlatform {

    String getPlatformName();

    Optional<ModInformation> getModInformation(String modId);

    Optional<String> getModDisplayName(String namespace);

    default Optional<SemanticVersion> getMinecraftVersion() {
        return getModVersion("minecraft");
    }

    Optional<SemanticVersion> getModVersion(String namespace);

    boolean isModLoaded(String namespace);

    Collection<String> getModIdList(boolean loadedOnly);

    /**
     * Gets the path to the Minecraft config instance directory (./minecraft/config)
     */
    Path getConfigPath();

    /**
     * Gets the path to a mod's configuration directory. If it doesn't exist it will be created.  If for some reason
     * it cannot be created, the standard Minecraft config path will be returned.
     *
     * @param modId ModId to obtain the configuration path.
     * @return Path to the mod's configuration directory.
     */
    default Path getConfigPath(final String modId) {
        var configDir = this.getConfigPath();
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

    KeyMapping registerKeyBinding(String translationKey, int code, String category);

    Collection<Path> findResourcePaths(String fileNamePattern);

    /**
     * Gets all the root paths for each loaded mod.
     */
    Map<String, List<Path>> getResourceRootPaths();

    /**
     * Obtains a mod configuration screen factory for generating configuration screens
     */
    Optional<IScreenFactory<?>> getModConfigScreenFactory(Class<? extends ConfigurationData> configClass);
}
