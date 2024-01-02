package org.orecruncher.dsurround.platform.fabric.services;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.KeyMapping;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.lib.platform.IPlatform;
import org.orecruncher.dsurround.lib.platform.ModInformation;
import org.orecruncher.dsurround.lib.version.SemanticVersion;

import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class PlatformServiceImpl implements IPlatform {

    public Optional<ModInformation> getModInformation(String modId) {
        var container = FabricLoader.getInstance().getModContainer(modId);
        if (container.isPresent()) {
            try {
                var metadata = container.get().getMetadata();
                var data = metadata.getCustomValue("dsurround").getAsObject();
                var displayName = metadata.getName();
                var version = SemanticVersion.parse(metadata.getVersion().getFriendlyString());
                var updateURL = data.get("updateURL").getAsString();
                var curseForgeLink = data.get("curseForgeLink").getAsString();
                var modrinthLink = data.get("modrinthLink").getAsString();
                var result = new ModInformation(modId, displayName, version, updateURL, curseForgeLink, modrinthLink);
                return Optional.of(result);
            } catch(Exception ex) {
                Client.LOGGER.error(ex, "What?");
            }
        }
        return Optional.empty();
    }

    public Optional<String> getModDisplayName(String namespace) {
        var container = FabricLoader.getInstance().getModContainer(namespace);
        return container.map(modContainer -> modContainer.getMetadata().getName());
    }

    public Optional<SemanticVersion> getModVersion(String namespace) {
        var container = FabricLoader.getInstance().getModContainer(namespace);
        if (container.isPresent()) {
            try {
                return Optional.of(SemanticVersion.parse(container.get().getMetadata().getVersion().getFriendlyString()));
            } catch (Exception ignored) {

            }
        }
        return Optional.empty();
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

    public Path getConfigPath() {
        return FabricLoader.getInstance().getConfigDir();
    }

    public Set<Path> getResourcePaths(String path) {
        Set<Path> out = new HashSet<>();

        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            mod.findPath(path).ifPresent(out::add);
        }

        return out;
    }

    @Override
    public KeyMapping registerKeyBinding(String translationKey, int code, String category) {
        return KeyBindingHelper.registerKeyBinding(new KeyMapping(translationKey, code, category));
    }
}
