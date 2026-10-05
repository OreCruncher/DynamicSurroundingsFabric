package org.orecruncher.dsurround.lib.resources;

import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.logging.IModLog;

import java.util.Collection;

import static org.orecruncher.dsurround.Configuration.Flags.RESOURCE_LOADING;

/**
 * Finds an asset file, such as sounds.json, in every namespace of the mod jars and resource packs. Not limited to
 * installed mods: like vanilla, it takes a resource pack's own namespaces too (a pack can add "mypack:" sounds).
 * <p>
 * Looks the file up in each namespace, as vanilla's sound manager does, rather than listing it: listing is for
 * folders, and not every pack implementation lists a single file (NeoForge's mod packs don't).
 */
public class ClientResourceFinder extends AbstractResourceFinder {

    private final ResourceManager resourceManager;

    protected ClientResourceFinder(IModLog logger, ResourceManager resourceManager) {
        super(logger);
        this.resourceManager = resourceManager;
    }

    @Override
    public <T> Collection<DiscoveredResource<T>> find(Codec<T> codec, String assetPath) {
        var results = new ObjectArray<DiscoveredResource<T>>();
        // Sorted, so the files are read in the same order every time
        var namespaces = this.resourceManager.getNamespaces().stream().sorted().toList();
        for (var namespace : namespaces) {
            var location = Identifier.tryBuild(namespace, assetPath);
            if (location == null)
                continue;
            var stack = this.resourceManager.getResourceStack(location);
            if (!stack.isEmpty())
                this.logger.debug(RESOURCE_LOADING, "[%s] - %d found", location, stack.size());
            for (var resource : stack)
                this.readInto(location, namespace, location, resource::open, codec, results);
        }
        return results;
    }
}
