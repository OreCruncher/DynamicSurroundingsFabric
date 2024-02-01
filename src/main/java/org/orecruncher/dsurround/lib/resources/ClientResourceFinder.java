package org.orecruncher.dsurround.lib.resources;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.logging.IModLog;

import java.nio.charset.Charset;
import java.util.Collection;
import java.util.HashMap;

import static org.orecruncher.dsurround.Configuration.Flags.RESOURCE_LOADING;

public class ClientResourceFinder extends AbstractResourceFinder {

    private final ResourceManager resourceManager;

    protected ClientResourceFinder(IModLog logger, ResourceManager resourceManager) {
        super(logger);
        this.resourceManager = resourceManager;
    }

    @Override
    public <T> Collection<DiscoveredResource<T>> find(Codec<T> codec, String assetPath) {

        var results = new HashMap<ResourceLocation, Collection<DiscoveredResource<T>>>();

        this.logger.debug(RESOURCE_LOADING, "[%s] - Locating assets", assetPath);

        var assets = this.resourceManager.listResourceStacks(assetPath, location -> true);
        if (assets.isEmpty()) {
            this.logger.debug(RESOURCE_LOADING, "[%s] - No assets found", assetPath);
            return ImmutableList.of();
        }

        var x = this.resourceManager.listResourceStacks("dsconfigs", location -> true);

        this.logger.debug(RESOURCE_LOADING, "[%s] - %d entries found", assetPath, assets.size());
        for (var kvp : assets.entrySet()) {
            this.logger.debug(RESOURCE_LOADING, "[%s] - Processing %s", assetPath, kvp.getKey());
            var resultList = results.computeIfAbsent(kvp.getKey(), i -> new ObjectArray<>());

            for (var resource : kvp.getValue()) {
                try (var inputStream = resource.open()) {
                    var assetBytes = inputStream.readAllBytes();
                    var assetString = new String(assetBytes, Charset.defaultCharset());
                    var entity = this.decode(kvp.getKey(), assetString, codec);
                    entity.ifPresent(e -> resultList.add(new DiscoveredResource<>(kvp.getKey().getNamespace(), e)));
                    this.logger.debug(RESOURCE_LOADING, "[%s] - Completed decode of %s", assetPath, kvp.getKey());
                } catch (Throwable t) {
                    this.logger.error(t, "[%s] - Unable to read resource stream for path %s", assetPath, kvp.getKey());
                }
            }
        }

        return results.values().stream().flatMap(Collection::stream).toList();
    }
}
