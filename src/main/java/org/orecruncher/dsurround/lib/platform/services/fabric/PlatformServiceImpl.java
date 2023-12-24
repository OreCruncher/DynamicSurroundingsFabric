package org.orecruncher.dsurround.lib.platform.services.fabric;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.Version;
import net.minecraft.client.option.KeyBinding;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.lib.platform.IPlatform;
import org.orecruncher.dsurround.lib.platform.ModInformation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class PlatformServiceImpl implements IPlatform {

    public Optional<ModInformation> getModInformation(String modId) {
        var container = FabricLoader.getInstance().getModContainer(modId);
        if (container.isPresent()) {
            var metadata = container.get().getMetadata();
            var data = metadata.getCustomValue("dsurround").getAsObject();
            var displayName = metadata.getName();
            var version = metadata.getVersion();
            var updateURL = data.get("updateURL").getAsString();
            var curseForgeLink = data.get("curseForgeLink").getAsString();
            var modrinthLink = data.get("modrinthLink").getAsString();
            var result = new ModInformation(modId, displayName, version, updateURL, curseForgeLink, modrinthLink);
            return Optional.of(result);
        }
        return Optional.empty();
    }

    public Optional<String> getModDisplayName(String namespace) {
        var container = FabricLoader.getInstance().getModContainer(namespace);
        return container.map(modContainer -> modContainer.getMetadata().getName());
    }

    public Optional<Version> getModVersion(String namespace) {
        var container = FabricLoader.getInstance().getModContainer(namespace);
        return container.map(modContainer -> modContainer.getMetadata().getVersion());
    }

    public boolean isModLoaded(String namespace) {
        return FabricLoader.getInstance().isModLoaded(namespace);
    }

    public Collection<String> getModIdList(boolean loadedOnly) {
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
    public Path getConfigPath(final String modId) {
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

    @Override
    public KeyBinding registerKeyBinding(String translationKey, int code, String category) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding(translationKey, code, category));
    }
}
