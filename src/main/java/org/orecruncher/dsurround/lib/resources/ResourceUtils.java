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

public final class ResourceUtils {

    static final IModLog LOGGER = Library.getLogger();
    private static final IPlatform PLATFORM = Library.getPlatform();

    /**
     * Scans the local disk as well as resource packs and JARs locating and creating accessors for the resource file
     * in question.
     *
     * @param diskPath Location on disk where external configs can be cached
     * @param config   The config file that is of interest
     * @return A collection of resource accessors that match the config criteria
     */
    public static Collection<IResourceAccessor> findResources(final File diskPath, final String config) {
        var packResources = new ArrayList<>(collectFromResourcePacks(config));
        packResources.addAll(collectFromDisk(diskPath, config));
        return packResources;
    }

    private static Collection<IResourceAccessor> collectFromResourcePacks(final String config) {
        return findResources("dsconfigs/%s".formatted(config));
    }

    private static Collection<IResourceAccessor> collectFromDisk(File diskPath, final String config) {
        Map<ResourceLocation, IResourceAccessor> result = new HashMap<>();
        var loadedMods = PLATFORM.getModIdList(true);
        for (var mod : loadedMods) {
            ResourceLocation location = new ResourceLocation(mod, config);
            IResourceAccessor accessor = IResourceAccessor.createExternalResource(diskPath, location);
            if (accessor.exists())
                result.put(location, accessor);
        }

        return result.values();
    }

    /**
     * Scans resource packs locating resource files.
     *
     * @return Collection of accessors to retrieve resource configurations.
     */
    public static Collection<IResourceAccessor> findResources(String configId) {
        return findResources(GameUtils.getResourceManager(), configId);
    }

    /**
     * Processes local tag definitions from resource packs and mods. Since Dynamic Surroundings is
     * a client mod special tagging is lost when connecting to a remote server. This routine helps
     * backfill that knowledge gap.
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

    private static Collection<IResourceAccessor> findResources(ResourceManager resourceManager, String assetName) {

        var results = new HashMap<ResourceLocation, Collection<IResourceAccessor>>();
        var namespaces = resourceManager.getNamespaces();

        for (var namespace : namespaces) {
            var location = new ResourceLocation(namespace, assetName);
            List<Resource> list = resourceManager.getResourceStack(new ResourceLocation(namespace, assetName));
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
