package org.orecruncher.dsurround.lib;

import joptsimple.internal.Strings;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.api.metadata.ModMetadata;
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

    public static Optional<ModCustomData> getModCustomData(String modId, String dataKey) {

        var container = FabricLoader.getInstance().getModContainer(modId);
        if (container.isEmpty())
            return Optional.empty();

        var property = container.get().getMetadata().getCustomValue(dataKey);
        if (property != null && property.getType() == CustomValue.CvType.OBJECT) {
            var data = new ModCustomData(property.getAsObject());
            return Optional.of(data);
        }

        return Optional.empty();
    }

    public static @Nullable String getModDisplayName(String namespace) {
        var container = FabricLoader.getInstance().getModContainer(namespace);
        return container.map(modContainer -> modContainer.getMetadata().getName()).orElse(null);
    }

    public static @Nullable Version getMinecraftVersion() {
        return getModVersion("minecraft");
    }

    public static @Nullable Version getModVersion(String namespace) {
        var container = FabricLoader.getInstance().getModContainer(namespace);
        return container.map(modContainer -> modContainer.getMetadata().getVersion()).orElse(null);
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

    public static class ModCustomData {

        private final CustomValue.CvObject dataMap;

        ModCustomData(CustomValue.CvObject data) {
            this.dataMap = data;
        }

        public CustomValue get(String keyPath) {
            if (Strings.isNullOrEmpty(keyPath))
                return this.dataMap;

            var segments = keyPath.split("\\.");

            return find(this.dataMap, segments, 0);
        }

        private static @Nullable CustomValue find(CustomValue cv, String[] pathSegments, int idx) {
            // If the search was exhausted, or if the current type is not an object we didn't
            // find it.
            var maxLevel = pathSegments.length - 1;
            if (idx > maxLevel || cv.getType() != CustomValue.CvType.OBJECT)
                return null;

            // Get any potential value at this level
            var value = cv.getAsObject().get(pathSegments[idx]);

            // If we didn't find return null
            if (value == null)
                return null;

            // If we hit the end return what we got
            if (idx == maxLevel)
                return value;

            // Recurse to the next level
            return find(value, pathSegments, idx + 1);
        }

        public String getString(String path) {
            var value = get(path);
            if (value != null && value.getType() == CustomValue.CvType.STRING)
                return value.getAsString();
            return null;
        }
    }
}
