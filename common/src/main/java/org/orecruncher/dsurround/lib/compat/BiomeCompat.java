package org.orecruncher.dsurround.lib.compat;

import it.unimi.dsi.fastutil.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.lib.reflection.ReflectionHelper;
import org.orecruncher.dsurround.mixinutils.IBiomeExtended;

import java.lang.reflect.InvocationTargetException;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Runtime-safe accessors for biome data used by Dynamic Surroundings. Routines will prefer mixin IBiomeExtended, but
 * if there is a runtime error of some sort (like linking), they will fall back to using reflection to access the various
 * properties in Biome.
**/
public final class BiomeCompat {

    private static final float DEFAULT_TEMP = 0.5F;
    private static final float DEFAULT_DOWNFALL = 0.0F;

    private static BiFunction<Biome, BlockPos, Float> biomeTemp;
    private static Function<Biome, Float> biomeDownfall;
    private static Function<Biome, Optional<BiomeSpecialEffects>> biomeSpecialEffects;

    private BiomeCompat() {
    }

    public static float getTemperature(Biome biome, @Nullable BlockPos pos) {
        if (biome == null) {
            return DEFAULT_TEMP;
        }

        if (pos == null)
            return biome.getBaseTemperature();

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
                            var result = ReflectionHelper.<Float>cast(method.get().invoke(biome, pos));
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

    public static float getDownfall(@Nullable Biome biome) {
        if (biome == null) {
            return DEFAULT_DOWNFALL;
        }

        // Use the last method that worked
        if (biomeDownfall != null) {
            return biomeDownfall.apply(biome);
        }

        Pair<Function<Biome, Float>, Float> choice = ReflectionHelper.choose(
                "Biome::climateSettings",
                biome,
                b -> {
                    var extended = asExtended(biome);
                    return extended.map(iBiomeExtended -> iBiomeExtended.dsurround_getWeather().downfall()).orElseThrow();
                },
                b -> {
                    var field = ReflectionHelper.findField(Biome.class, "climateSettings");
                    if (field.isPresent()) {
                        try {
                            var settings = ReflectionHelper.<Biome.ClimateSettings>cast(field.get().get(biome));
                            if (settings.isPresent()) {
                                return settings.get().downfall();
                            }
                            throw new RuntimeException("Field '%s' not present".formatted("climateSettings"));
                        } catch (IllegalAccessException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    throw new RuntimeException("Field '%s' is not present".formatted("climateSettings"));
                },
                b -> DEFAULT_DOWNFALL
        );

        biomeDownfall = choice.first();
        return choice.second();
    }

    public static Optional<BiomeSpecialEffects> getSpecialEffects(Biome biome) {
        if (biome == null) {
            return Optional.empty();
        }

        if (biomeSpecialEffects != null) {
            return biomeSpecialEffects.apply(biome);
        }

        Pair<Function<Biome, Optional<BiomeSpecialEffects>>, Optional<BiomeSpecialEffects>> choice = ReflectionHelper.choose(
                "Biome::getSpecialEffects",
                biome,
                b -> {
                    var extended = asExtended(biome);
                    return extended.map(IBiomeExtended::dsurround_getSpecialEffects);
                },
                b -> {
                    var field = ReflectionHelper.findField(Biome.class, "specialEffects");
                    if (field.isPresent()) {
                        try {
                            return ReflectionHelper.cast(field.get().get(b));
                        } catch (IllegalAccessException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    throw new RuntimeException("Field '%s' is not present".formatted("specialEffects"));
                },
                b -> Optional.empty()
        );

        biomeSpecialEffects = choice.first();
        return choice.second();
    }

    private static Optional<IBiomeExtended> asExtended(Biome biome) {
        // The 26.x Biome class is not statically related to our mixin interface at
        // compile time.  Cast through Object so javac allows the runtime mixin
        // interface check.
        return ReflectionHelper.cast(biome);
    }
}