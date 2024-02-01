package org.orecruncher.dsurround.lib.resources;

import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.platform.IPlatform;

import java.nio.file.Files;
import java.util.Collection;

import static org.orecruncher.dsurround.Configuration.Flags.RESOURCE_LOADING;

public class ServerResourceFinder extends AbstractResourceFinder {

    private final IPlatform platform;

    protected ServerResourceFinder(IModLog logger, IPlatform platform) {
        super(logger);
        this.platform = platform;
    }

    @Override
    public <T> Collection<DiscoveredResource<T>> find(Codec<T> codec, String assetPath) {

        Collection<DiscoveredResource<T>> results = new ObjectArray<>();

        var resource = ResourceLocation.tryParse(assetPath);
        assert resource != null;

        var filePath = assetPath.replace(":", "/");
        if (!filePath.endsWith(".json"))
            filePath = filePath + ".json";

        for (var path : this.platform.findResourcePaths(filePath)) {
            this.logger.debug(RESOURCE_LOADING, "[%s] - Processing %s", resource, path.toString());
            try {
                var content = Files.readString(path);
                this.decode(resource, content, codec).ifPresent(r -> results.add(new DiscoveredResource<>(resource.getNamespace(), r)));
                this.logger.debug(RESOURCE_LOADING, "[%s] - Completed decode of %s", resource, path);
            } catch (Throwable t) {
                this.logger.error(t, "[%s] - Unable to read %s", resource, path.toString());
            }
        }

        return results;
    }
}