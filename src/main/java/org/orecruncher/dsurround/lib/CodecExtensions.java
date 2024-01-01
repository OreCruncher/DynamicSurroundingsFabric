package org.orecruncher.dsurround.lib;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.ListCodec;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.block.state.BlockState;
import org.orecruncher.dsurround.lib.block.BlockStateMatcher;
import org.orecruncher.dsurround.lib.block.MatchOnBlockTag;

import java.io.StringReader;
import java.util.Optional;
import java.util.function.Function;

/**
 * Extensions to the Codec serialization framework.
 */
public interface CodecExtensions<A> extends Codec<A> {
    /**
     * Checks that a string is a valid format specification for BlockState
     */
    static Codec<IMatcher<BlockState>> checkBlockStateSpecification(boolean allowTags) {
        final Function<IMatcher<BlockState>, DataResult<IMatcher<BlockState>>> func = value -> {
            if (!allowTags && value instanceof MatchOnBlockTag)
                return DataResult.error(() -> String.format("Current context does not allow block matching based on tags (%s)", value));
            return DataResult.success(value);
        };
        return BlockStateMatcher.CODEC.flatXmap(func, func);
    }

    static <A> Optional<A> deserialize(String content, Codec<A> codec) {
        var reader = new StringReader(content);
        Dynamic<JsonElement> dynamic;
        if (codec instanceof ListCodec) {
            JsonArray jsonArray = GsonHelper.parseArray(reader);
            dynamic = new Dynamic<>(JsonOps.INSTANCE, jsonArray);
        } else if (codec instanceof MapCodec) {
            // Not sure if there is anything special yet...
            JsonObject jsonObject = GsonHelper.parse(reader);
            dynamic = new Dynamic<>(JsonOps.INSTANCE, jsonObject);
        } else {
            JsonObject jsonObject = GsonHelper.parse(reader);
            dynamic = new Dynamic<>(JsonOps.INSTANCE, jsonObject);
        }
        try {
            DataResult<A> result = codec.parse(dynamic);
            return result.resultOrPartial(Library.getLogger()::warn);
        } catch (Throwable t) {
            Library.getLogger().error(t, "Unable to parse input");
        }

        return Optional.empty();
    }
}
