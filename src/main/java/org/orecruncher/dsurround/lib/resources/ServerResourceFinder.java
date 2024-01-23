package org.orecruncher.dsurround.lib.resources;

import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.collections.ObjectArray;

import java.nio.file.Files;
import java.util.Collection;

import static org.orecruncher.dsurround.Configuration.Flags.RESOURCE_PARSING;

public class ServerResourceFinder<T> extends AbstractResourceFinder<T> {
    protected ServerResourceFinder(Codec<T> decoder, String pathPrefix) {
        super(decoder, pathPrefix);
    }

    @Override
    public Collection<DiscoveredResource<T>> find(String assetPath) {
        return this.find(new ResourceLocation(Library.MOD_ID, assetPath));
    }

    @Override
    public Collection<DiscoveredResource<T>> find(ResourceLocation resource) {
        Collection<DiscoveredResource<T>> results = new ObjectArray<>();
        var tagFilePath = "%s/%s/%s/%s".formatted(PackType.SERVER_DATA.getDirectory(), resource.getNamespace(), this.pathPrefix, resource.getPath());

        if (!tagFilePath.endsWith(".json"))
            tagFilePath = tagFilePath + ".json";

        for (var path : this.getPlatform().findResourcePaths(tagFilePath)) {
            LOGGER.debug(RESOURCE_PARSING, "Processing %s from resource pack", path.toString());
            try {
                var content = Files.readString(path);
                this.decode(resource, content).ifPresentOrElse(
                        r -> results.add(new DiscoveredResource<>(resource.getNamespace(), r)),
                        () -> LOGGER.warn("Unable to parse file %s", resource));
            } catch (Throwable t) {
                LOGGER.error(t, "Unable to read file %s", path.toString());
            }
        }

        return results;
    }
}
