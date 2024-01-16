package org.orecruncher.dsurround.lib.resources;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagManager;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.platform.IPlatform;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Function;

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
        Map<ResourceLocation, IResourceAccessor> accessorMap = new HashMap<>();
        collectFromResourcePacks(accessorMap, config);
        collectFromDisk(accessorMap, diskPath, config);
        return new ArrayList<>(accessorMap.values());
    }

    private static void collectFromResourcePacks(final Map<ResourceLocation, IResourceAccessor> accessorMap, final String config) {
        var results = findAssets(
                PLATFORM::isModLoaded,
                ns -> new ResourceLocation(ns, String.format("dsconfigs/%s", config)));
        for (var e : results) {
            accessorMap.put(e.location(), e);
        }
    }

    private static void collectFromDisk(final Map<ResourceLocation, IResourceAccessor> accessorMap, File diskPath, final String config) {
        // Gather loaded mods.  We focus on those from within the JAR
        var loadedMods = PLATFORM.getModIdList(true);
        for (var mod : loadedMods) {
            ResourceLocation location = new ResourceLocation(mod, config);
            IResourceAccessor accessor = IResourceAccessor.createExternalResource(diskPath, location);
            if (accessor.exists())
                accessorMap.put(location, accessor);
        }
    }

    /**
     * Scans resource packs locating resource files.
     *
     * @return Collection of accessors to retrieve resource configurations.
     */
    public static Collection<IResourceAccessor> findResources(String configId) {
        return findAssets(
                ns -> true,
                ns -> new ResourceLocation(ns, configId));
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

    // Modeled after sound list processing in SoundManager
    private static Collection<IResourceAccessor> findAssets(Function<String, Boolean> namespaceFilter, Function<String, ResourceLocation> identitySupplier) {
        final List<IResourceAccessor> results = new ArrayList<>();
        var resourceManager = GameUtils.getResourceManager();

        for (var namespace : resourceManager.getNamespaces()) {
            try {
                if (!namespaceFilter.apply(namespace))
                    continue;

                var location = identitySupplier.apply(namespace);
                var resourceList = resourceManager.getResourceStack(location);

                try {
                    for (var resource : resourceList) {
                        try (InputStream inputStream = resource.open()) {
                            byte[] asset = inputStream.readAllBytes();
                            IResourceAccessor accessor = IResourceAccessor.createRawBytes(location, asset);
                            results.add(accessor);
                        } catch (Throwable t) {
                            LOGGER.error(t, "Unable to read the asset %s from the resource pack", location);
                        }
                    }
                } catch (Throwable t) {
                    LOGGER.error(t, "Unable to enumerate resource for %s", location);
                }
            } catch (Throwable ignore) {
                // Suppress - get a lot of not founds for packs that do not have a resource
            }
        }

        return results;
    }
}
