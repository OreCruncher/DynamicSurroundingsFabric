package org.orecruncher.dsurround.lib;

import com.mojang.serialization.Codec;

/**
 * Small immutable weight value used by Dynamic Surroundings' own weighted
 * tables. Minecraft 26.x removed the old util.random.Weight wrapper.
 */
public record WeightValue(int asInt) {

    public static final Codec<WeightValue> CODEC = Codec.INT.xmap(WeightValue::of, WeightValue::asInt);

    public static WeightValue of(int value) {
        return new WeightValue(Math.max(0, value));
    }

    @Override
    public String toString() {
        return Integer.toString(this.asInt);
    }
}
