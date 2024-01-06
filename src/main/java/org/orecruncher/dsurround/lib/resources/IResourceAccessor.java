package org.orecruncher.dsurround.lib.resources;

import com.google.gson.*;
import com.mojang.serialization.*;
import joptsimple.internal.Strings;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.lib.CodecExtensions;

import java.io.File;
import java.io.FileNotFoundException;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.function.Consumer;

/**
 * A resource accessor is used to obtain the content of a resource from within the JAR or from an external disk source.
 */
public interface IResourceAccessor {

    /**
     * Creates a reference to a resource accessor that can be used to retrieve data on the local disk.
     *
     * @param root     The location on disk where the data can be found.
     * @param location The resource location of the data that needs to be retrieved.
     * @return Reference to a resource accessor to obtain the necessary data.
     */
    static IResourceAccessor createExternalResource(final File root, final ResourceLocation location) {
        return new ResourceAccessorExternal(root, location);
    }

    /**
     * Creates a reference to a resource accessor that is backed by a byte array
     * supplied by the caller.
     *
     * @param location Location of the resource
     * @param bytes    Bytes representing the asset
     * @return Reference to a resource accessor to obtain the necessary data
     */
    static IResourceAccessor createRawBytes(final ResourceLocation location, byte[] bytes) {
        return new ResourceAccessorBytes(location, bytes);
    }

    /**
     * Iterates over a collection of accessors invoking an operation.  The operation is logged and encapsulated within
     * an error handling for logging purposes.  Exceptions will be suppressed.
     *
     * @param accessors Collection of accessors to invoke
     * @param consumer  The routine to invoke on each accessor.
     */
    static void process(final Collection<IResourceAccessor> accessors, final Consumer<IResourceAccessor> consumer) {
        for (final IResourceAccessor accessor : accessors) {
            ResourceUtils.LOGGER.info("Processing %s", accessor);
            try {
                consumer.accept(accessor);
            } catch (final Throwable t) {
                ResourceUtils.LOGGER.error(t, "Unable to complete processing of %s", accessor);
            }
        }
    }

    /**
     * The resource location for the accessor
     *
     * @return Resource location
     */
    ResourceLocation location();

    /**
     * Obtains the content of the resource as a string
     *
     * @return The resource data as a string, or null if not found
     */
    default String asString() {
        byte[] bytes = this.asBytes();
        return bytes != null ? new String(bytes, Charset.defaultCharset()) : null;
    }

    /**
     * Obtains the content of the resource as a series of bytes
     *
     * @return The resource data as an array of bytes, or null if not found
     */
    byte[] asBytes();

    /**
     * Obtains the content of the resource as a deserialized object
     *
     * @param clazz Class of the object to deserialize
     * @param <T>   The type of object that is being deserialized
     * @return Reference to the deserialized object, null if not possible
     */
    default <T> T as(final Class<T> clazz) {
        String content = this.asString();
        if (!Strings.isNullOrEmpty(content)) {
            try {
                final Gson gson = new GsonBuilder().create();
                return gson.fromJson(content, clazz);
            } catch (final Throwable t) {
                ResourceUtils.LOGGER.error(t, "Unable to complete processing of %s", this.toString());
            }
        }
        return null;
    }

    /**
     * Determines if the resource exists
     *
     * @return true if it exists, false otherwise
     */
    default boolean exists() {
        return asBytes() != null;
    }

    /**
     * Obtains the content of the resource as a deserialized object of the type specified.
     *
     * @param type Type of object instance to deserialize
     * @param <T>  The object type for casting
     * @return Reference to the deserialized object, null if not possible
     */
    default <T> T as(final Type type) {
        String content = this.asString();
        if (!Strings.isNullOrEmpty(content)) {
            try {
                final Gson gson = new GsonBuilder().create();
                return gson.fromJson(content, type);
            } catch (final Throwable t) {
                ResourceUtils.LOGGER.error(t, "Unable to complete processing of %s", this.toString());
            }
        }
        return null;
    }

    /**
     * Obtains the content of the resource as a deserialized object using the codec provided.
     *
     * @param codec Codec to use when deserializing
     * @param <T>   The object type for casting
     * @return Reference to the deserialized object, null if not possible
     */
    default <T> @Nullable T as(final Codec<T> codec) {
        String content = this.asString();
        if (!Strings.isNullOrEmpty(content)) {
            var result = CodecExtensions.deserialize(content, codec);
            if (result.isPresent())
                return result.get();
        }
        return null;
    }

    default void logError(final Throwable t) {
        if (t instanceof FileNotFoundException)
            ResourceUtils.LOGGER.debug("Asset not found for %s", this.toString());
        else
            ResourceUtils.LOGGER.error(t, "Unable to process asset %s", this.toString());
    }
}
