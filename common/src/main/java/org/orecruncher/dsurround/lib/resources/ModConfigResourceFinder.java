package org.orecruncher.dsurround.lib.resources;

import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.logging.IModLog;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Predicate;

import static org.orecruncher.dsurround.Configuration.Flags.RESOURCE_LOADING;

/**
 * Finds the mod's configuration files in mod jars and resource packs: assets/(namespace)/(config folder)/..., one
 * folder per mod. A folder whose namespace isn't an installed mod is skipped, so configuration for mods that aren't
 * there (including what this mod ships for others) isn't loaded.
 * <p>
 * The files are listed once, when this is made, and looked up by their exact path within the config folder.
 */
public class ModConfigResourceFinder extends AbstractResourceFinder {

    private record Found(ResourceLocation location, List<Resource> stack) {
    }

    // By path within the config folder (e.g. "blocks.json", "tags/block/effects/fireflies.json"), in the order the
    // resource manager lists them
    private final Map<String, List<Found>> byPath = new HashMap<>();

    /**
     * @param configPath  the config folder within each namespace's assets ("dsconfigs")
     * @param isModLoaded whether a mod is installed, by its id; folders of others are skipped
     */
    public ModConfigResourceFinder(IModLog logger, ResourceManager resourceManager, String configPath, Predicate<String> isModLoaded) {
        super(logger);

        var prefix = configPath + "/";
        var skipped = new TreeSet<String>();
        for (var entry : resourceManager.listResourceStacks(configPath, location -> true).entrySet()) {
            var location = entry.getKey();
            if (!isModLoaded.test(location.getNamespace())) {
                skipped.add(location.getNamespace());
                continue;
            }
            var path = location.getPath();
            if (path.startsWith(prefix))
                this.byPath.computeIfAbsent(path.substring(prefix.length()), p -> new ArrayList<>()).add(new Found(location, entry.getValue()));
        }

        if (!skipped.isEmpty())
            this.logger.debug(RESOURCE_LOADING, "Skipping %s configuration for mods that aren't installed: %s", configPath, skipped);
    }

    @Override
    public <T> Collection<DiscoveredResource<T>> find(Codec<T> codec, String path) {
        var found = this.byPath.get(withJsonExtension(path));
        if (found == null)
            return List.of();

        var results = new ObjectArray<DiscoveredResource<T>>();
        for (var f : found) {
            for (var resource : f.stack())
                this.readInto(f.location(), f.location().getNamespace(), f.location(), resource::open, codec, results);
        }
        return results;
    }
}
