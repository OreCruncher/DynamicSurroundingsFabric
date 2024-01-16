package org.orecruncher.dsurround.lib.resources;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagManager;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.platform.IPlatform;

import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Predicate;

@SuppressWarnings("unused")
public final class ResourceUtils {

    /**
     * Name of the resource container where Dynamic Surrounding's configuration jsons can be located in other mod
     * assets.
     */
    public static final String CONFIG_RESOURCE_CONTAINER_NAME = "dsconfigs";

    static final IModLog LOGGER = Library.getLogger();
    private static final IPlatform PLATFORM = Library.getPlatform();

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
     * @param diskPath Location on disk where external configs can be cached
     * @param assetPath Path to the asset that is of interest
     * @return A collection of resource accessors that match the assetPath criteria
     */
    public static Collection<IResourceAccessor> findResources(final File diskPath, final String assetPath) {
        var packResources = new ArrayList<>(collectFromResourcePacks(assetPath));
        packResources.addAll(collectFromDisk(diskPath, assetPath));
        return packResources;
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
    public static Collection<IResourceAccessor> findResources(String assetPath) {
        return findResources(GameUtils.getResourceManager(), assetPath, location -> PLATFORM.isModLoaded(location.getNamespace()));
    }

    /**
     * Processes local tag definitions from resource packs and mods. Since Dynamic Surroundings is
     * a client mod special tagging is lost when connecting to a remote server. This routine helps
     * backfill that knowledge gap.
     *
     * @param tagKey Tag key instance that is the subject of the search
     * @return Collection of IResourceAccessors for assets that were identified
     */
    public static Collection<IResourceAccessor> findClientTagFiles(TagKey<?> tagKey) {

        final Collection<IResourceAccessor> results = new ArrayList<>();

        var tagIdentifier = tagKey.location();
        var tagType = TagManager.getTagDir(tagKey.registry());
        var tagFile = "%s/%s/%s/%s.json".formatted(PackType.SERVER_DATA.getDirectory(), tagIdentifier.getNamespace(), tagType, tagIdentifier.getPath());

        for (var path : PLATFORM.findResourcePaths(tagFile)) {
            try {
                var asset = Files.readAllBytes(path);
                IResourceAccessor accessor = IResourceAccessor.createRawBytes(tagIdentifier, asset);
                results.add(accessor);
            } catch (Throwable t) {
                LOGGER.error(t, "Unable to read resource stream for path %s", path.toString());
            }
        }

        return results;
    }

    private static Collection<IResourceAccessor> collectFromResourcePacks(final String assetPath) {
        return findResources("%s/%s".formatted(CONFIG_RESOURCE_CONTAINER_NAME, assetPath));
    }

    private static Collection<IResourceAccessor> collectFromDisk(File diskPath, final String assetPath) {
        Map<ResourceLocation, IResourceAccessor> result = new HashMap<>();
        var loadedMods = PLATFORM.getModIdList(true);
        for (var mod : loadedMods) {
            ResourceLocation location = new ResourceLocation(mod, assetPath);
            IResourceAccessor accessor = IResourceAccessor.createExternalResource(diskPath, location);
            if (accessor.exists())
                result.put(location, accessor);
        }

        return result.values();
    }

    private static Collection<IResourceAccessor> findResources(ResourceManager resourceManager, String assetPath) {
        return findResources(resourceManager, assetPath, location -> true);
    }

    private static Collection<IResourceAccessor> findResources(ResourceManager resourceManager, String assetPath, Predicate<ResourceLocation> locationFilter) {

        var results = new HashMap<ResourceLocation, Collection<IResourceAccessor>>();
        var namespaces = resourceManager.getNamespaces();

        for (var namespace : namespaces) {
            var location = new ResourceLocation(namespace, assetPath);
            if (!locationFilter.test(location))
                continue;
            List<Resource> list = resourceManager.getResourceStack(location);
            if (!list.isEmpty()) {
                var resultList = results.computeIfAbsent(location, i -> new ObjectArray<>());
                for (var resource : list) {
                    try (var inputStream = resource.open()) {
                        var asset = inputStream.readAllBytes();
                        IResourceAccessor accessor = IResourceAccessor.createRawBytes(location, asset);
                        resultList.add(accessor);
                    } catch (Throwable t) {
                        LOGGER.error(t, "Unable to read resource stream for path %s", location);
                    }
                }
            }
        }

        return results.values().stream().flatMap(Collection::stream).toList();
    }
}
