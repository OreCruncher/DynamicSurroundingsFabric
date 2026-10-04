package org.orecruncher.dsurround.lib.weighted;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import org.jetbrains.annotations.NotNull;

/**
 * Small immutable weight value used by Dynamic Surroundings' own weighted tables, from 0 to {@link #MAX}.
 */
public record WeightValue(int asInt) {

    /**
     * The largest weight. Weights come from data packs and are added up when choosing, so without a limit a few
     * huge ones would overflow the total, and the choice would silently pick nothing. The mod's own weights are
     * all far below this.
     */
    public static final int MAX = 1_000_000;

    /**
     * An integer from 0 to {@link #MAX}. Anything else is a parse error, so only the entry it is in is dropped: an
     * exception here would abandon the whole file.
     */
    public static final Codec<WeightValue> CODEC = Codec.INT.comapFlatMap(
            value -> isValid(value)
                    ? DataResult.success(new WeightValue(value))
                    : DataResult.error(() -> "Weight must be from 0 to " + MAX + ": " + value),
            WeightValue::asInt);

    /**
     * @throws IllegalArgumentException if {@code asInt} isn't from 0 to {@link #MAX}
     */
    public WeightValue {
        if (!isValid(asInt))
            throw new IllegalArgumentException("Weight must be from 0 to " + MAX + ": " + asInt);
    }

    /**
     * @throws IllegalArgumentException if {@code value} isn't from 0 to {@link #MAX}
     */
    public static WeightValue of(int value) {
        return new WeightValue(value);
    }

    private static boolean isValid(int value) {
        return value >= 0 && value <= MAX;
    }

    @Override
    public @NotNull String toString() {
        return Integer.toString(this.asInt);
    }
}
