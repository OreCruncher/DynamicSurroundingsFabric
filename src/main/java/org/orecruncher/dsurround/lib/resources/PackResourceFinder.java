package org.orecruncher.dsurround.lib.resources;

import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.collections.ObjectArray;

import java.nio.file.Files;
import java.util.Collection;

import static org.orecruncher.dsurround.Configuration.Flags.RESOURCE_LOADING;

public class PackResourceFinder<T> extends AbstractResourceFinder<T> {

    protected PackResourceFinder(PackType packType, Codec<T> decoder, String pathPrefix) {
        super(decoder, pathPrefix);
    }

    @Override
    public Collection<DiscoveredResource<T>> find(String assetPath) {
        return this.find(new ResourceLocation(Library.MOD_ID, assetPath));
    }

    @Override
    public Collection<DiscoveredResource<T>> find(ResourceLocation resource) {
        Collection<DiscoveredResource<T>> results = new ObjectArray<>();
        var tagFilePath = resource.getPath();

        if (!tagFilePath.endsWith(".json"))
            tagFilePath = tagFilePath + ".json";

        for (var path : this.getPlatform().findResourcePaths(tagFilePath)) {
            LOGGER.debug(RESOURCE_LOADING, "[%s] - Processing %s", resource, path.toString());
            try {
                var content = Files.readString(path);
                this.decode(resource, content).ifPresent(r -> results.add(new DiscoveredResource<>(resource.getNamespace(), r)));
                LOGGER.debug(RESOURCE_LOADING, "[%s] - Completed decode of %s", resource, path);
            } catch (Throwable t) {
                LOGGER.error(t, "[%s] - Unable to read %s", resource, path.toString());
            }
        }

        return results;
    }
}