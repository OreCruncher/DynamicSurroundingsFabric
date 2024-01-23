package org.orecruncher.dsurround.lib.resources;

import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;
import org.orecruncher.dsurround.lib.CodecExtensions;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.collections.ObjectArray;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;

import static org.orecruncher.dsurround.Configuration.Flags.RESOURCE_PARSING;

public class DiskResourceFinder<T> extends AbstractResourceFinder<T> {

    private final Path diskLocation;

    DiskResourceFinder(Codec<T> decoder, String pathPrefix, Path diskLocation) {
        super(decoder, pathPrefix);
        this.diskLocation = diskLocation;
    }

    @Override
    public Collection<DiscoveredResource<T>> find(ResourceLocation location) {
        return this.find(location.getPath());
    }

    @Override
    public Collection<DiscoveredResource<T>> find(String assetPath) {
        Collection<DiscoveredResource<T>> result = new ObjectArray<>();

        var fileName = assetPath;
        if (!fileName.endsWith(".json"))
            fileName = fileName + ".json";

        var resourceManager = GameUtils.getResourceManager();
        var namespaces = resourceManager.getNamespaces();

        for (var namespace : namespaces) {
            var location = new ResourceLocation(namespace, assetPath);
            var filePath = Paths.get(this.diskLocation.toString(), namespace, this.pathPrefix, fileName);
            if (Files.exists(filePath)) {
                LOGGER.debug(RESOURCE_PARSING, "Processing %s file from disk", filePath.toString());
                try {
                    var content = Files.readString(filePath);
                    CodecExtensions.deserialize(content, this.decoder).ifPresent(e -> result.add(new DiscoveredResource<>(namespace, e)));
                } catch (Throwable t) {
                    LOGGER.error(t, "Unable to read resource stream for path %s", location);
                }
            }
        }

        return result;
    }
}
