package org.orecruncher.dsurround.lib.resources;

import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import org.orecruncher.dsurround.eventing.ClientState;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.events.HandlerPriority;
import org.orecruncher.dsurround.lib.logging.IModLog;

import java.nio.file.Files;
import java.util.Collection;

import static org.orecruncher.dsurround.Configuration.Flags.RESOURCE_LOADING;

public class ServerResourceFinder extends AbstractResourceFinder {

    private static final ResourceLookupHelper lookupHelper;

    static {
        lookupHelper = new ResourceLookupHelper(PackType.SERVER_DATA);
        ClientState.RESOURCE_RELOAD.register(rm -> lookupHelper.refresh(), HandlerPriority.VERY_HIGH);
    }

    protected ServerResourceFinder(IModLog logger) {
        super(logger);
    }

    @Override
    public <T> Collection<DiscoveredResource<T>> find(Codec<T> codec, String assetPath) {

        Collection<DiscoveredResource<T>> results = new ObjectArray<>();

        var resource = ResourceLocation.tryParse(assetPath);
        assert resource != null;

        var filePath = assetPath.replace(":", "/");
        if (!filePath.endsWith(".json"))
            filePath = filePath + ".json";

        for (var path : lookupHelper.findResourcePaths(filePath)) {
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