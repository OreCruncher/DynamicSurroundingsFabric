package org.orecruncher.dsurround.lib;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.ListCodec;
import net.minecraft.block.BlockState;
import net.minecraft.util.JsonHelper;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.lib.block.BlockStateMatcher;
import org.orecruncher.dsurround.lib.block.MatchOnBlockTag;
import org.orecruncher.dsurround.lib.block.MatchOnMaterial;

import java.io.StringReader;
import java.util.Optional;
import java.util.function.Function;

/**
 * Extensions to the Codec serialization framework.
 */
public interface CodecExtensions<A> extends Codec<A> {
    /**
     * Checks that a string is a valid format for HTML color coding.
     */
    static Codec<String> checkHTMLColor() {
        final Function<String, DataResult<String>> func = value -> {
            if (PatternValidation.HTML_COLOR_ENCODING.matcher(value).matches()) {
                return DataResult.success(value);
            }
            return DataResult.error(String.format("%s is not a valid HTML color description", value));
        };
        return Codec.STRING.flatXmap(func, func);
    }

    /**
     * Checks that a string is a valid format specification for BlockState
     */
    static Codec<IMatcher<BlockState>> checkBlockStateSpecification(boolean allowTags, boolean allowMaterials) {
        final Function<IMatcher<BlockState>, DataResult<IMatcher<BlockState>>> func = value -> {
            if (!allowTags && value instanceof MatchOnBlockTag)
                return DataResult.error(String.format("Current context does not allow block matching based on tags (%s)", value));
            if (!allowMaterials && value instanceof MatchOnMaterial)
                return DataResult.error(String.format("Current context does not allow block matching based on materials (%s)", value));
            return DataResult.success(value);
        };
        return BlockStateMatcher.CODEC.flatXmap(func, func);
    }

    static <A> Optional<A> deserialize(String content, Codec<A> codec) {
        var reader = new StringReader(content);
        Dynamic<JsonElement> dynamic;
        if (codec instanceof ListCodec) {
            JsonArray jsonArray = JsonHelper.method_37165(reader);
            dynamic = new Dynamic<>(JsonOps.INSTANCE, jsonArray);
        } else if (codec instanceof MapCodec) {
            // Not sure if there is anything special yet...
            JsonObject jsonObject = JsonHelper.deserialize(reader);
            dynamic = new Dynamic<>(JsonOps.INSTANCE, jsonObject);
        } else {
            JsonObject jsonObject = JsonHelper.deserialize(reader);
            dynamic = new Dynamic<>(JsonOps.INSTANCE, jsonObject);
        }
        try {
            DataResult<A> result = codec.parse(dynamic);
            return result.resultOrPartial(Client.LOGGER::warn);
        } catch (Throwable t) {
            Client.LOGGER.error(t, "Unable to parse input");
        }

        return Optional.empty();
    }
}
