package org.orecruncher.dsurround.lib.resources;

import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import org.orecruncher.dsurround.lib.codec.CodecExtensions;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.logging.ModLog;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Optional;

import static org.orecruncher.dsurround.Configuration.Flags.RESOURCE_LOADING;

public abstract class AbstractResourceFinder implements IResourceFinder {

    /**
     * Opens a resource's content, however it is stored.
     */
    @FunctionalInterface
    protected interface Opener {
        InputStream open() throws IOException;
    }

    protected final IModLog logger;

    protected AbstractResourceFinder(IModLog logger) {
        this.logger = ModLog.createChild(logger, "ResourceFinder");
    }

    /**
     * Reads a resource (as UTF-8) and decodes it, adding the result to {@code results}. A resource that can't be read
     * or decoded is logged and skipped, so one bad file doesn't stop the others from loading.
     *
     * @param location  what is being read, for decoding errors and the log
     * @param namespace whose it is, recorded with the result
     * @param where     where it was found, for the log
     */
    protected <T> void readInto(Identifier location, String namespace, Object where, Opener opener, Codec<T> codec,
                                Collection<DiscoveredResource<T>> results) {
        this.logger.debug(RESOURCE_LOADING, "[%s] - Processing %s", location, where);
        try (var stream = opener.open()) {
            var content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            this.decode(location, content, codec).ifPresent(r -> results.add(new DiscoveredResource<>(namespace, r)));
            this.logger.debug(RESOURCE_LOADING, "[%s] - Completed decode of %s", location, where);
        } catch (Throwable t) {
            this.logger.error(t, "[%s] - Unable to read %s", location, where);
        }
    }

    protected <T> Optional<T> decode(Identifier location, String content, Codec<T> decoder) {
        this.logger.debug(RESOURCE_LOADING, "[%s] - Decoding resource", location);
        var result = CodecExtensions.deserialize(location.toString(), content, decoder);
        if (this.logger.isTracing(RESOURCE_LOADING))
            if (result.isPresent())
                this.logger.debug(RESOURCE_LOADING, "[%s] - Content successfully decoded", location);
            else
                this.logger.debug(RESOURCE_LOADING, "[%s] - No content", location);
        return result;
    }

    /**
     * {@code path} with ".json" on the end, if it hasn't already.
     */
    protected static String withJsonExtension(String path) {
        return path.endsWith(".json") ? path : path + ".json";
    }
}
