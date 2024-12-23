package org.orecruncher.dsurround.lib.resources;

import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;
import org.orecruncher.dsurround.lib.CodecExtensions;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.logging.ModLog;

import java.util.Optional;

import static org.orecruncher.dsurround.Configuration.Flags.RESOURCE_LOADING;

public abstract class AbstractResourceFinder implements IResourceFinder {

    protected final IModLog logger;

    protected AbstractResourceFinder(IModLog logger) {
        this.logger = ModLog.createChild(logger, "ResourceFinder");
    }

    protected <T> Optional<T> decode(ResourceLocation location, String content, Codec<T> decoder) {
        this.logger.debug(RESOURCE_LOADING, "[%s] - Decoding resource", location);
        var result = CodecExtensions.deserialize(content, decoder);
        if (this.logger.isTracing(RESOURCE_LOADING))
            if (result.isPresent())
                this.logger.debug(RESOURCE_LOADING, "[%s] - Content successfully decoded", location);
            else
                this.logger.debug(RESOURCE_LOADING, "[%s] - No content", location);
        return result;
    }
}
