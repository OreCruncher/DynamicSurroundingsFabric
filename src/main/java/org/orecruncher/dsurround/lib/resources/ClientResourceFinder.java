package org.orecruncher.dsurround.lib.resources;

import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.collections.ObjectArray;

import java.nio.charset.Charset;
import java.util.Collection;
import java.util.HashMap;

import static org.orecruncher.dsurround.Configuration.Flags.RESOURCE_PARSING;

public class ClientResourceFinder<T> extends AbstractResourceFinder<T> {
    protected ClientResourceFinder(Codec<T> decoder, String pathPrefix) {
        super(decoder, pathPrefix);
    }

    @Override
    public Collection<DiscoveredResource<T>> find(ResourceLocation  location) {
        return this.find(location.getPath());
    }

    @Override
    public Collection<DiscoveredResource<T>> find(String assetPath) {

        if (!this.pathPrefix.isEmpty())
            assetPath = this.pathPrefix + "/" + assetPath;

        if (!assetPath.endsWith(".json"))
            assetPath = assetPath + ".json";

        var results = new HashMap<ResourceLocation, Collection<DiscoveredResource<T>>>();
        var resourceManager = GameUtils.getResourceManager();

        LOGGER.debug(RESOURCE_PARSING, "Locating assets %s", assetPath);

        var assets = resourceManager.listResourceStacks(assetPath, location -> true);
        if (!assets.isEmpty()) {
            LOGGER.debug(RESOURCE_PARSING, "%d entries found", assets.size());
            for (var kvp : assets.entrySet()) {
                LOGGER.debug(RESOURCE_PARSING, "Processing %s...", kvp.getKey());
                var resultList = results.computeIfAbsent(kvp.getKey(), i -> new ObjectArray<>());

                for (var resource : kvp.getValue()) {
                    try (var inputStream = resource.open()) {
                        var assetBytes = inputStream.readAllBytes();
                        var assetString = new String(assetBytes, Charset.defaultCharset());
                        var entity = this.decode(kvp.getKey(), assetString);
                        entity.ifPresent(e -> resultList.add(new DiscoveredResource<>(kvp.getKey().getNamespace(), e)));
                    } catch (Throwable t) {
                        LOGGER.error(t, "Unable to read resource stream for path %s", kvp.getKey());
                    }
                }
            }
        }

        return results.values().stream().flatMap(Collection::stream).toList();
    }
}
