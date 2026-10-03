package org.orecruncher.dsurround.lib.weighted;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import org.jetbrains.annotations.NotNull;

/**
 * Small immutable weight value used by Dynamic Surroundings' own weighted
 * tables. Minecraft 26.x removed the old util.random.Weight wrapper.
 */
public record WeightValue(int asInt) {

    /**
     * A non-negative integer. A negative one is a parse error, so only the entry it is in is dropped: an exception
     * here would abandon the whole file.
     */
    public static final Codec<WeightValue> CODEC = Codec.INT.comapFlatMap(
            value -> value < 0
                    ? DataResult.error(() -> "Weight must not be negative: " + value)
                    : DataResult.success(new WeightValue(value)),
            WeightValue::asInt);

    public static WeightValue of(int value) {
        if (value < 0)
            throw new RuntimeException("Weighted value must be non-negative");
        return new WeightValue(value);
    }

    @Override
    public @NotNull String toString() {
        return Integer.toString(this.asInt);
    }
}