package org.orecruncher.dsurround.lib.compat;

import it.unimi.dsi.fastutil.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.lib.reflection.ReflectionHelper;
import org.orecruncher.dsurround.mixinutils.IBiomeExtended;

import java.lang.reflect.InvocationTargetException;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * Runtime-safe accessors for biome data used by Dynamic Surroundings. Routines will prefer mixin IBiomeExtended, but
 * if there is a runtime error of some sort (like linking), they will fall back to using reflection to access the various
 * properties in Biome.
**/
public final class BiomeCompat {

    private static final float DEFAULT_TEMP = 0.5F;

    private static BiFunction<Biome, BlockPos, Float> biomeTemp;

    private BiomeCompat() {
    }

    public static float getTemperature(Biome biome, @Nullable BlockPos pos) {
        if (biome == null) {
            return DEFAULT_TEMP;
        }

        if (pos == null) {
            return biome.getBaseTemperature();
        }
        if (biomeTemp != null) {
            return biomeTemp.apply(biome, pos);
        }

        Pair<BiFunction<Biome, BlockPos, Float>, Float> choice = ReflectionHelper.choose(
                "Biome::getTemperature",
                biome,
                pos,
                (b, p) -> {
                    var extended = asExtended(biome);
                    return extended.map(iBiomeExtended -> iBiomeExtended.dsurround_getTemperature(pos)).orElseThrow();
                },
                (b, p) -> {
                    var method = ReflectionHelper.findMethod(Biome.class, "getTemperature", BlockPos.class);
                    if (method.isPresent()) {
                        try {
                            var result = ReflectionHelper.cast(method.get().invoke(biome, pos), Float.class);
                            if (result.isPresent()) {
                                return result.get();
                            }
                        } catch (IllegalAccessException | InvocationTargetException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    throw new RuntimeException("Method '%s' not present".formatted("getTemperature"));
                },
                (b, p) -> b.getBaseTemperature(),
                (b, p) -> DEFAULT_TEMP
        );

        biomeTemp = choice.first();
        return choice.second();
    }

    private static Optional<IBiomeExtended> asExtended(Biome biome) {
        return ReflectionHelper.cast(biome, IBiomeExtended.class);
    }
}