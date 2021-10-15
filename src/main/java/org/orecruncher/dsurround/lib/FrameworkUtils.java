package org.orecruncher.dsurround.lib;

import joptsimple.internal.Strings;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.minecraft.resource.ResourcePackProfile;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.Client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class FrameworkUtils {

    public static Optional<ModContainer> getModContainer(String namespace) {
        return FabricLoader.getInstance().getModContainer(namespace);
    }

    public static @Nullable String getModDisplayName(String namespace) {
        var container = FabricLoader.getInstance().getModContainer(namespace);
        return container.map(modContainer -> modContainer.getMetadata().getName()).orElse(null);
    }

    public static @Nullable Version getModVersion(String namespace) {
        var container = FabricLoader.getInstance().getModContainer(namespace);
        return container.map(modContainer -> modContainer.getMetadata().getVersion()).orElse(null);
    }

    public static @Nullable String getModCustomProperty(String namespace, String propertyName) {
        var container = FabricLoader.getInstance().getModContainer(namespace);
        return container.map(modContainer -> modContainer.getMetadata().getCustomValue(propertyName).getAsString()).orElse(null);
    }

    public static boolean isModLoaded(String namespace) {
        return FabricLoader.getInstance().isModLoaded(namespace);
    }

    public static String getModBranding(String namespace) {
        var container = FabricLoader.getInstance().getModContainer(namespace);
        if (container.isPresent()) {
            ModMetadata data = container.get().getMetadata();
            return String.format("%s v%s", data.getName(), data.getVersion());
        }
        return Strings.EMPTY;
    }

    public static Collection<String> getModIdList(boolean loadedOnly) {
        return FabricLoader.getInstance()
                .getAllMods()
                .stream()
                .map(container -> container.getMetadata().getId())
                .filter(name -> !loadedOnly || FabricLoader.getInstance().isModLoaded(name))
                .collect(Collectors.toList());
    }

    public static Collection<ResourcePackProfile> getEnabledResourcePacks() {
        var rpm = GameUtils.getResourcePackManager();
        return rpm.getEnabledProfiles();
    }

    /**
     * Gets the path to a mod's configuration directory. If it doesn't exist it will be created.  If for some reason
     * it cannot be created, the standard Minecraft config path will be returned.
     *
     * @param modId ModId to obtain the configuration path.
     * @return Path to the mod's configuration directory.
     */
    public static Path getConfigPath(final String modId) {
        var configDir = FabricLoader.getInstance().getConfigDir();
        var configPath = configDir.resolve(Objects.requireNonNull(modId));

        if (Files.notExists(configPath))
            try {
                Files.createDirectory(configPath);
            } catch (final IOException ex) {
                Client.LOGGER.error(ex, "Unable to create directory path %s", configPath.toString());
                configPath = configDir;
            }

        return configPath;
    }
}
