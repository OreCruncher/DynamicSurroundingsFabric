package org.orecruncher.dsurround.lib.resources;

import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.logging.IModLog;

import java.nio.charset.Charset;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.orecruncher.dsurround.Configuration.Flags.RESOURCE_LOADING;

public class ModConfigResourceFinder extends AbstractResourceFinder {

    private final Map<ResourceLocation, List<Resource>> resources;

    public ModConfigResourceFinder(IModLog logger, ResourceManager resourceManager, String configPath) {
        super(logger);
        this.resources = resourceManager.listResourceStacks(configPath, location -> true);
    }

    public <T> Collection<DiscoveredResource<T>> find(Codec<T> codec, String path) {
        if (!path.endsWith(".json"))
            path = path + ".json";

        var result = new ObjectArray<DiscoveredResource<T>>();
        for (var kvp : this.resources.entrySet()) {
            var resourcePath = kvp.getKey().getPath();
            if (resourcePath.endsWith(path)) {
                this.logger.debug(RESOURCE_LOADING, "[%s] - Processing %s", resourcePath, kvp.getKey());
                for (var r : kvp.getValue()) {
                    try (var inputStream = r.open()) {
                        var assetBytes = inputStream.readAllBytes();
                        var assetString = new String(assetBytes, Charset.defaultCharset());
                        var entity = this.decode(kvp.getKey(), assetString, codec);
                        entity.ifPresent(e -> result.add(new DiscoveredResource<>(kvp.getKey().getNamespace(), e)));
                        this.logger.debug(RESOURCE_LOADING, "[%s] - Completed decode of %s", resourcePath, kvp.getKey());
                    } catch (Throwable t) {
                        this.logger.error(t, "[%s] - Unable to read resource stream for path %s", resourcePath, kvp.getKey());
                    }
                }
            }
        }

        return result;
    }
}
