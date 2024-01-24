package org.orecruncher.dsurround.lib.resources;

import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;
import org.orecruncher.dsurround.lib.CodecExtensions;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.logging.IModLog;

import java.util.Optional;

import static org.orecruncher.dsurround.Configuration.Flags.RESOURCE_LOADING;

public abstract class AbstractResourceFinder<T> implements IResourceFinder<T> {

    protected static final IModLog LOGGER = ContainerManager.resolve(IModLog.class);

    protected final Codec<T> decoder;
    protected final String pathPrefix;

    protected AbstractResourceFinder(Codec<T> decoder, String pathPrefix) {
        this.decoder = decoder;
        this.pathPrefix = pathPrefix;
    }

    protected Optional<T> decode(ResourceLocation location, String content) {
        LOGGER.debug(RESOURCE_LOADING, "[%s] - Decoding resource", location);
        var result = CodecExtensions.deserialize(content, this.decoder);
        if (LOGGER.isTracing(RESOURCE_LOADING))
            if (result.isPresent())
                LOGGER.debug(RESOURCE_LOADING, "[%s] - Content successfully decoded", location);
            else
                LOGGER.debug(RESOURCE_LOADING, "[%s] - No content", location);
        return result;
    }
}
