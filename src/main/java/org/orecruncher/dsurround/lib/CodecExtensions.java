package org.orecruncher.dsurround.lib;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import org.orecruncher.dsurround.lib.block.BlockStateParser;

import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Extensions to the Codec serialization framework.
 */
public interface CodecExtensions<A> extends Codec<A> {
    /**
     * Checks that a string is a valid format for HTML color coding.
     */
    static Codec<String> checkHTMLColor() {
        final Function<String, DataResult<String>> func = value -> {
            if (Pattern.compile("^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$").matcher(value).matches()) {
                return DataResult.success(value);
            }
            return DataResult.error(String.format("%s is not a valid HTML color description", value));
        };
        return Codec.STRING.flatXmap(func, func);
    }

    /**
     * Checks that a string is a valid format specification for BlockState
     */
    static Codec<String> checkBlockStateSpecification() {
        final Function<String, DataResult<String>> func = value -> {
            var result = BlockStateParser.parse(value);
            if (result.isPresent())
                return DataResult.success(value);
            return DataResult.error(String.format("%s is not a valid block state specification", value));
        };
        return Codec.STRING.flatXmap(func, func);
    }
}
