package org.orecruncher.dsurround.lib.resources;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.lib.FrameworkUtils;
import org.orecruncher.dsurround.lib.logging.IModLog;

import java.io.File;
import java.util.*;
import java.util.function.Function;

@Environment(EnvType.CLIENT)
public final class ResourceUtils {

    static final IModLog LOGGER = Client.LOGGER.createChild(ResourceUtils.class);
    private static final String CONFIG_FOLDER = Client.ModId + "_configs/";

    /**
     * Scans the local disk as well as resource packs and JARs locating and creating accessors for the config file
     * in question.  Configs on disk have priority over resource packs and JARs, and 3rd party jars have priority
     * over the provided mod ID.  These resources are read from CLIENT resources (assets) rather than SERVER
     * resources (data).
     *
     * @param modId  ID of the current mod doing the processing
     * @param diskPath   Location on disk where external configs can be cached
     * @param config The config file that is of interest
     * @return A collection of resource accessors that match the config criteria
     */
    public static Collection<IResourceAccessor> findConfigs(final String modId, final File diskPath, final String config) {
        Map<Identifier, IResourceAccessor> accessorMap = new HashMap<>();
        collectFromJar(accessorMap, modId, config);
        collectFromResourcePacks(accessorMap, modId, config);
        collectFromDisk(accessorMap, diskPath, config);
        return new ArrayList<>(accessorMap.values());
    }

    private static void collectFromJar(final Map<Identifier, IResourceAccessor> accessorMap, final String modId, final String config) {
        // Gather loaded mods.  We focus on those from within the JAR
        var root = modId + "/configs";
        var loadedMods = FrameworkUtils.getModIdList(true);
        for (var mod : loadedMods) {
            // Skip the current mod
            if (mod.equals(modId)) {
                continue;
            }

            if (mod.equals("minecraft")) {
                int x = 0;
            }

            Identifier location = new Identifier(mod, config);
            IResourceAccessor accessor = IResourceAccessor.createJarResource(root, location);
            if (accessor.exists())
                accessorMap.put(location, accessor);
        }
    }

    private static void collectFromResourcePacks(final Map<Identifier, IResourceAccessor> accessorMap, final String modId, final String config) {
        String path = "dsconfigs/" + config;
        var results = findAssets(ns -> new Identifier(ns, path));
        for (var e : results) {
            accessorMap.put(e.location(), e);
        }
    }

    private static void collectFromDisk(final Map<Identifier, IResourceAccessor> accessorMap, File diskPath, final String config) {
        // Gather loaded mods.  We focus on those from within the JAR
        var loadedMods = FrameworkUtils.getModIdList(true);
        for (var mod : loadedMods) {
            Identifier location = new Identifier(mod, config);
            IResourceAccessor accessor = IResourceAccessor.createExternalResource(diskPath, location);
            if (accessor.exists())
                accessorMap.put(location, accessor);
        }
    }

    /**
     * Scans resource packs locating sound.json configurations.
     *
     * @return Collection of accessors to retrieve sound.json configurations.
     */
    public static Collection<IResourceAccessor> findSounds() {
        return findAssets(ns -> new Identifier(ns, "sounds.json"));
    }

    private static Collection<IResourceAccessor> findAssets(Function<String, Identifier> identitySupplier) {
        final List<IResourceAccessor> results = new ArrayList<>();
        final Collection<ResourcePackProfile> packs = FrameworkUtils.getEnabledResourcePacks();

        for (final ResourcePackProfile pack : packs) {
            final ResourcePack rp = pack.createResourcePack();
            final Set<String> embeddedNamespaces = rp.getNamespaces(ResourceType.CLIENT_RESOURCES);
            for (final String ns : embeddedNamespaces) {
                final Identifier location = identitySupplier.apply(ns);
                final IResourceAccessor accessor = IResourceAccessor.createPackResource(rp, location, location);
                if (accessor.exists()) {
                    results.add(accessor);
                }
            }
        }

        return results;
    }

    /**
     * Obtains the string content of the resource at the specified asset location with the JAR
     *
     * @param location The resource to load
     * @return The content of the specified resource, or null if not found
     */
    @Nullable
    public static String readResource(final Identifier location) {
        return readResource("", location);
    }

    /**
     * Obtains the string content of the resource at the specified asset location with the JAR
     *
     * @param root     Location is relative to this root in the JAR
     * @param location The resource to load
     * @return The content of the specified resource, or null if not found
     */
    @Nullable
    public static String readResource(final String root, final Identifier location) {
        final IResourceAccessor accessor = IResourceAccessor.createJarResource(root, location);
        return accessor.asString();
    }
}
