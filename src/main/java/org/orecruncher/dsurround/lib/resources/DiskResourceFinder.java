package org.orecruncher.dsurround.lib.resources;

import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;
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
    public Collection<DiscoveredResource<T>> find(ResourceLocation resource) {
        Collection<DiscoveredResource<T>> result = new ObjectArray<>();
        try {
            // Make sure we are looking for a json file. Sometimes the resource will not have
            // an extension.
            var fileName = resource.getPath();
            if (!fileName.endsWith(".json"))
                fileName = fileName + ".json";

            var filePath = Paths.get(this.diskLocation.toString(), resource.getNamespace(), this.pathPrefix, fileName);
            if (Files.exists(filePath)) {
                LOGGER.debug(RESOURCE_PARSING, "Processing %s file from disk", filePath.toString());
                var content = Files.readString(filePath);
                this.decode(resource, content).ifPresentOrElse(
                        r -> result.add(new DiscoveredResource<>(resource.getNamespace(), r)),
                        () -> LOGGER.warn("Unable to parse file %s", resource));
            }
        } catch (Throwable t) {
            LOGGER.error(t, "Unable to read resource stream for path %s", resource);
        }

        return result;
    }
}
