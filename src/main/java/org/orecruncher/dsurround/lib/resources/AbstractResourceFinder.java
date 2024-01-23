package org.orecruncher.dsurround.lib.resources;

import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;
import org.orecruncher.dsurround.lib.CodecExtensions;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.logging.IModLog;

import java.util.Optional;

import static org.orecruncher.dsurround.Configuration.Flags.RESOURCE_PARSING;

public abstract class AbstractResourceFinder<T> implements IResourceFinder<T> {

    protected static final IModLog LOGGER = ContainerManager.resolve(IModLog.class);

    protected final Codec<T> decoder;
    protected final String pathPrefix;

    protected AbstractResourceFinder(Codec<T> decoder, String pathPrefix) {
        this.decoder = decoder;
        this.pathPrefix = pathPrefix;
    }

    protected Optional<T> decode(ResourceLocation location, String content) {
        LOGGER.debug(RESOURCE_PARSING, "Parsing resource %s", location);
        return CodecExtensions.deserialize(content, this.decoder);
    }
}
