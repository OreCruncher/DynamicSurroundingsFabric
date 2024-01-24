package org.orecruncher.dsurround.lib.resources;

import com.mojang.serialization.Codec;
import net.minecraft.tags.TagFile;
import net.minecraft.tags.TagKey;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.util.IMinecraftDirectories;

import java.util.*;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
public final class ResourceUtils {

    private static final IMinecraftDirectories MINECRAFT_DIRECTORIES = ContainerManager.resolve(IMinecraftDirectories.class);

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
    public static <T> Collection<DiscoveredResource<T>> findModResources(Codec<T> codec, final String assetPath) {
        var finder = IResourceFinder.createFinderForModConfiguration(codec, MINECRAFT_DIRECTORIES.getModDataDirectory(), "");
        return finder.find(assetPath);
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
    public static <T> Collection<DiscoveredResource<T>> findResources(Codec<T> codec, String assetPath) {
        var finder = IResourceFinder.createClientFinder(codec, "");
        return finder.find(assetPath);
    }

    /**
     * Processes local tag definitions from resource packs and mods. Since Dynamic Surroundings is
     * a client mod special tagging is lost when connecting to a remote server. This routine helps
     * backfill that knowledge gap.
     *
     * @param tagKey Tag key instance that is the subject of the search
     * @return Collection of TagFile instances that were found
     */
    public static Collection<TagFile> findClientTagFiles(TagKey<?> tagKey) {
        var finder = IResourceFinder.createFinderForTag(tagKey, MINECRAFT_DIRECTORIES.getModDataDirectory());
        var result = finder.find(tagKey.location());
        return result.stream().map(DiscoveredResource::resourceContent).collect(Collectors.toList());
    }
}
