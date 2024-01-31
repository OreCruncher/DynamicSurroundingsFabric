package org.orecruncher.dsurround.fabric.services;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.config.libraries.AssetLibraryEvent;
import org.orecruncher.dsurround.config.libraries.IReloadEvent;
import org.orecruncher.dsurround.eventing.ClientState;
import org.orecruncher.dsurround.fabric.config.ClothAPIFactory;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.config.ConfigurationData;
import org.orecruncher.dsurround.lib.config.IScreenFactory;
import org.orecruncher.dsurround.lib.platform.IPlatform;
import org.orecruncher.dsurround.lib.platform.ModInformation;
import org.orecruncher.dsurround.lib.resources.ResourceUtilities;
import org.orecruncher.dsurround.lib.resources.ResourceLookupHelper;
import org.orecruncher.dsurround.lib.version.SemanticVersion;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class PlatformServiceImpl implements IPlatform {

    private final ResourceLookupHelper lookupHelper;

    public PlatformServiceImpl() {

        this.lookupHelper = new ResourceLookupHelper(PackType.SERVER_DATA);

        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {

            private final ResourceLocation id = new ResourceLocation(Constants.MOD_ID, "client_resource_listener");
            @Override
            public ResourceLocation getFabricId() {
                return this.id;
            }

            @Override
            public void onResourceManagerReload(@NotNull ResourceManager resourceManager) {
                if (GameUtils.getMC().isSameThread()) {
                    Library.LOGGER.info("Refreshing lookup helper");
                    PlatformServiceImpl.this.lookupHelper.refresh(PlatformServiceImpl.this);

                    Library.LOGGER.info("Resource reload - resetting configuration caches");
                    var resourceUtilities = ResourceUtilities.createForResourceManager(resourceManager);
                    AssetLibraryEvent.RELOAD.raise().onReload(resourceUtilities, IReloadEvent.Scope.RESOURCES);
                }
            }
        });

        CommonLifecycleEvents.TAGS_LOADED.register((registry, client) -> {
            if (client)
                ClientState.TAG_SYNC.raise().onTagSync(registry);
        });
    }

    @Override
    public String getPlatformName() {
        return "Fabric";
    }

    @Override
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
                Library.LOGGER.error(ex, "What?");
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<String> getModDisplayName(String namespace) {
        var container = FabricLoader.getInstance().getModContainer(namespace);
        return container.map(modContainer -> modContainer.getMetadata().getName());
    }

    @Override
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

    @Override
    public boolean isModLoaded(String namespace) {
        return FabricLoader.getInstance().isModLoaded(namespace);
    }

    @Override
    public Collection<String> getModIdList(boolean loadedOnly) {
        return FabricLoader.getInstance()
                .getAllMods()
                .stream()
                .map(container -> container.getMetadata().getId())
                .filter(name -> !loadedOnly || FabricLoader.getInstance().isModLoaded(name))
                .collect(Collectors.toList());
    }

    @Override
    public Path getConfigPath() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public KeyMapping registerKeyBinding(String translationKey, int code, String category) {
        return KeyBindingHelper.registerKeyBinding(new KeyMapping(translationKey, code, category));
    }

    @Override
    public Collection<Path> findResourcePaths(String fileNamePattern) {
        return this.lookupHelper.findResourcePaths(fileNamePattern);
    }

    @Override
    public Collection<Path> getResourceRootPaths(PackType packType) {
        var pathPrefix = packType.getDirectory();
        return FabricLoader.getInstance().getAllMods()
                .stream()
                .map(mod -> mod.findPath(pathPrefix))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }

    @Override
    public Optional<IScreenFactory<?>> getModConfigScreenFactory(Class<? extends ConfigurationData> configClass) {
        IScreenFactory<?> result = null;
        if (this.isModLoaded(Constants.CLOTH_CONFIG))
            result = ClothAPIFactory.createDefaultConfigScreen(configClass);
        return Optional.ofNullable(result);
    }
}