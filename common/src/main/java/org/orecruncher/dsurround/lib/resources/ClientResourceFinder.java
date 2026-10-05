package org.orecruncher.dsurround.lib.resources;

import com.mojang.serialization.Codec;
import net.minecraft.server.packs.resources.ResourceManager;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.logging.IModLog;

import java.util.Collection;

import static org.orecruncher.dsurround.Configuration.Flags.RESOURCE_LOADING;

/**
 * Finds an asset file, such as sounds.json, in every namespace of the mod jars and resource packs. Not limited to
 * installed mods: like vanilla, it takes a resource pack's own namespaces too (a pack can add "mypack:" sounds).
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
        var assets = this.resourceManager.listResourceStacks(assetPath, location -> true);
        this.logger.debug(RESOURCE_LOADING, "[%s] - %d found", assetPath, assets.size());

        for (var entry : assets.entrySet()) {
            var location = entry.getKey();
            for (var resource : entry.getValue())
                this.readInto(location, location.getNamespace(), location, resource::open, codec, results);
        }
        return results;
    }
}
