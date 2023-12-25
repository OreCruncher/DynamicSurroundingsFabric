package org.orecruncher.dsurround.lib.platform;

import net.fabricmc.loader.api.Version;
import net.minecraft.client.option.KeyBinding;
import org.orecruncher.dsurround.lib.Library;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

public interface IPlatform {

    Optional<ModInformation> getModInformation(String modId);

    Optional<String> getModDisplayName(String namespace);

    default Optional<Version> getMinecraftVersion() {
        return getModVersion("minecraft");
    }

    Optional<Version> getModVersion(String namespace);

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
        var configDir =this.getConfigPath();
        var configPath = configDir.resolve(Objects.requireNonNull(modId));

        if (Files.notExists(configPath))
            try {
                Files.createDirectory(configPath);
            } catch (final IOException ex) {
                Library.getLogger().error(ex, "Unable to create directory path %s", configPath.toString());
                configPath = configDir;
            }

        return configPath;
    }

    KeyBinding registerKeyBinding(String translationKey, int code, String category);
}
