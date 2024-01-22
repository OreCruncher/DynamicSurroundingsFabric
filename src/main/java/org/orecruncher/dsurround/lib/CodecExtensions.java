package org.orecruncher.dsurround.lib;

import com.google.gson.JsonParser;
import com.mojang.serialization.*;
import net.minecraft.world.level.block.state.BlockState;
import org.orecruncher.dsurround.lib.block.BlockStateMatcher;
import org.orecruncher.dsurround.lib.block.MatchOnBlockTag;

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
        try {
            var jsonElement = JsonParser.parseString(content);
            var dynamic = new Dynamic<>(JsonOps.INSTANCE, jsonElement);
            DataResult<A> result = codec.parse(dynamic);
            return result.resultOrPartial(Library.LOGGER::warn);
        } catch (Throwable t) {
            Library.LOGGER.error(t, "Unable to parse input");
        }

        return Optional.empty();
    }
}
