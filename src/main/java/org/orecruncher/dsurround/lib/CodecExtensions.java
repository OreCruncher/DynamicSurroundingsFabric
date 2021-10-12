package org.orecruncher.dsurround.lib;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import org.orecruncher.dsurround.lib.block.BlockStateMatcher;
import org.orecruncher.dsurround.lib.block.BlockStateParseException;
import org.orecruncher.dsurround.lib.block.MatchOnBlockTag;
import org.orecruncher.dsurround.lib.block.MatchOnMaterial;

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
    static Codec<BlockStateMatcher> checkBlockStateSpecification(boolean allowTags, boolean allowMaterials) {
        final Function<BlockStateMatcher, DataResult<BlockStateMatcher>> func = value -> {
            if (!allowTags && value instanceof MatchOnBlockTag)
                return DataResult.error(String.format("Current context does not allow block matching based on tags (%s)", value));
            if (!allowMaterials && value instanceof MatchOnMaterial)
                return DataResult.error(String.format("Current context does not allow block matching based on tags (%s)", value));
            return DataResult.success(value);
        };
        return BlockStateMatcher.CODEC.flatXmap(func, func);
    }
}
