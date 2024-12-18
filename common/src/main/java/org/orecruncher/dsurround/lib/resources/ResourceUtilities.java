package org.orecruncher.dsurround.lib.resources;

import com.mojang.serialization.Codec;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagFile;
import net.minecraft.tags.TagKey;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.platform.IMinecraftDirectories;

import java.util.*;

@SuppressWarnings("unused")
public final class ResourceUtilities {

    private final IModLog logger;
    private final ModConfigResourceFinder modConfigHelper;
    private final DiskResourceFinder diskResourceHelper;
    private final ClientResourceFinder resourceFinder;
    private final ServerResourceFinder packResourceFinder;

    ResourceUtilities(IModLog modLog, IMinecraftDirectories minecraftDirectories, ResourceManager resourceManager) {
        this.logger = modLog;
        this.modConfigHelper = new ModConfigResourceFinder(this.logger, resourceManager, "dsconfigs");
        this.diskResourceHelper = new DiskResourceFinder(this.logger, minecraftDirectories.getModDataDirectory());
        this.resourceFinder = new ClientResourceFinder(this.logger, resourceManager);
        this.packResourceFinder = new ServerResourceFinder(this.logger);
    }

    /**
     * <p>
     * Scans the local disk as well as resource packs and JARs locating and creating accessors for the resource file
     * in question.
     * </p>
     * <p>
     * For mods, it is assumed that the resources will be found in the "dsconfigs" within the mod's assets
     * folder (ex. minecraft:dsconfigs/*.json).
     * </p>
     * <p>
     * For disk, it is assumed to be in the "dsurround/configs" folder in the configuration directory. Each folder
     * within will be named based on a mod's namespace. For example, if a folder is called "minecraft", those assets
     * within will be loaded if "minecraft" mod is loaded (which is always the case).
     * (Example: .minecraft/assetPath/dsurround/configs/minecraft/*.json)
     * </p>
     * @param assetPath Path to the asset that is of interest
     * @return A collection of resource accessors that match the assetPath criteria
     */
    public <T> Collection<DiscoveredResource<T>> findModResources(Codec<T> codec, final String assetPath) {
        var result = new ObjectArray<DiscoveredResource<T>>();
        result.addAll(this.modConfigHelper.find(codec, assetPath));
        result.addAll(this.diskResourceHelper.find(codec, assetPath));
        return result;
    }

    /**
     * <p>
     * Scans resource packs locating resource files. The resource path is relative to the root of a mods asset
     * folder (example: minecraft:resourcePath).
     *<p>
     * If a mod is not loaded, resource locations with that namespace will be filtered out.  For example, if
     * there was an asset folder "assets/modnotfound/dsconfig" it would be ignored.
     *
     * @param assetPath The path of the asset to find within the various resource locations
     * @return Collection of accessors to retrieve resource configurations.
     */
    public <T> Collection<DiscoveredResource<T>> findResources(Codec<T> codec, String assetPath) {
        return this.resourceFinder.find(codec, assetPath);
    }

    /**
     * Processes local tag definitions from resource packs and mods. Since Dynamic Surroundings is
     * a client mod special tagging is lost when connecting to a remote server. This routine helps
     * backfill that knowledge gap.
     *
     * @param tagKey Tag key instance that is the subject of the search
     * @return Collection of TagFile instances that were found
     */
    public Collection<TagFile> findClientTagFiles(TagKey<?> tagKey) {
        var result = new ObjectArray<DiscoveredResource<TagFile>>();
        var tagDir = Registries.tagsDirPath(tagKey.registry());
        var tagFolder = "%s/%s".formatted(tagDir, tagKey.location().getPath());
        var tagFolderPack = "%s:%s".formatted(tagKey.location().getNamespace(), tagFolder);
        result.addAll(this.packResourceFinder.find(TagFile.CODEC, tagFolderPack));
        result.addAll(this.modConfigHelper.find(TagFile.CODEC, tagFolder));
        result.addAll(this.diskResourceHelper.find(TagFile.CODEC, tagFolder));
        return result.stream().map(DiscoveredResource::resourceContent).toList();
    }

    public static ResourceUtilities createForCurrentState() {
        return createForResourceManager(GameUtils.getMC().getResourceManager());
    }

    public static ResourceUtilities createForResourceManager(ResourceManager resourceManager) {
        var logger = ContainerManager.resolve(IModLog.class);
        var directories = ContainerManager.resolve(IMinecraftDirectories.class);
        return new ResourceUtilities(logger, directories, resourceManager);
    }
}
