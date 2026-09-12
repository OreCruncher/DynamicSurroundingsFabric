package org.orecruncher.dsurround.lib.weighted;

import com.mojang.serialization.Codec;
import org.jetbrains.annotations.NotNull;

/**
 * Small immutable weight value used by Dynamic Surroundings' own weighted
 * tables. Minecraft 26.x removed the old util.random.Weight wrapper.
 */
public record WeightValue(int asInt) {

    public static final Codec<WeightValue> CODEC = Codec.INT.xmap(WeightValue::of, WeightValue::asInt);

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