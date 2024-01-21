package org.orecruncher.dsurround.lib.resources;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
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
        try {
            LOGGER.debug(RESOURCE_PARSING, "Parsing resource %s", location);
            var jsonElement = JsonParser.parseString(content);
            return this.decoder.parse(new Dynamic<>(JsonOps.INSTANCE, jsonElement)).result();
        } catch (Throwable t) {
            LOGGER.error(t, "Unable to parse content for %s", location.toString());
        }
        return Optional.empty();
    }
}
