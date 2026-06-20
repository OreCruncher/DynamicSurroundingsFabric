package org.orecruncher.dsurround.lib;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import org.orecruncher.dsurround.config.biome.BiomeInfo;
import org.orecruncher.dsurround.mixinutils.IBiomeExtended;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * Runtime-safe accessors for biome data used by Dynamic Surroundings.
 *
 * <p>On the 26.x client, optional mixin invokers can fail when target method
 * names change.  A failed invoker may still leave the target class marked with
 * {@link IBiomeExtended}, but without the generated bridge method, which causes
 * {@link AbstractMethodError} during the first client ticks.  This class keeps
 * the biome cache and temperature/downfall access safe even when that happens.</p>
 */
public final class BiomeCompat {

    private static final Map<Biome, BiomeInfo> INFO_CACHE = Collections.synchronizedMap(new WeakHashMap<>());

    private BiomeCompat() {
    }

    public static BiomeInfo getInfo(Biome biome) {
        if (biome == null) {
            return null;
        }

        var ext = asExtended(biome);
        if (ext != null) {
            try {
                var info = ext.dsurround_getInfo();
                if (info != null) {
                    return info;
                }
            } catch (LinkageError | RuntimeException ignored) {
                // Fall back to the external weak cache below.
            }
        }

        return INFO_CACHE.get(biome);
    }

    public static void setInfo(Biome biome, BiomeInfo info) {
        if (biome == null) {
            return;
        }

        if (info == null) {
            INFO_CACHE.remove(biome);
        } else {
            INFO_CACHE.put(biome, info);
        }

        var ext = asExtended(biome);
        if (ext != null) {
            try {
                ext.dsurround_setInfo(info);
            } catch (LinkageError | RuntimeException ignored) {
                // The weak cache above is sufficient when the mixin cache is unavailable.
            }
        }
    }

    public static float getTemperature(Biome biome, BlockPos pos) {
        if (biome == null) {
            return 0.5F;
        }

        if (pos != null) {
            var exact = invoke(biome, "getTemperature", new Class<?>[]{BlockPos.class}, pos)
                    .or(() -> invoke(biome, "temperature", new Class<?>[]{BlockPos.class}, pos));
            if (exact.isPresent()) {
                return asFloat(exact.get(), 0.5F);
            }
        }

        try {
            return biome.getBaseTemperature();
        } catch (LinkageError | RuntimeException ignored) {
            // Continue with reflective climate-settings access below.
        }

        Object climate = invokeAny(biome, "getModifiedClimateSettings", "getClimateSettings", "climateSettings")
                .or(() -> readField(biome, "climateSettings", "modifiedClimateSettings", "weather", "climate"))
                .orElse(null);
        if (climate != null) {
            var temperature = invokeAny(climate, "temperature", "getTemperature")
                    .or(() -> readField(climate, "temperature"));
            if (temperature.isPresent()) {
                return asFloat(temperature.get(), 0.5F);
            }
        }

        return 0.5F;
    }

    public static float getDownfall(Biome biome) {
        if (biome == null) {
            return 0.0F;
        }

        var ext = asExtended(biome);
        if (ext != null) {
            try {
                var weather = ext.dsurround_getWeather();
                if (weather != null) {
                    return weather.downfall();
                }
            } catch (LinkageError | RuntimeException ignored) {
                // Fall back to public/reflected climate settings below.
            }
        }

        Object climate = invokeAny(biome, "getModifiedClimateSettings", "getClimateSettings", "climateSettings")
                .or(() -> readField(biome, "climateSettings", "modifiedClimateSettings", "weather", "climate"))
                .orElse(null);
        if (climate != null) {
            var downfall = invokeAny(climate, "downfall", "getDownfall")
                    .or(() -> readField(climate, "downfall"));
            if (downfall.isPresent()) {
                return asFloat(downfall.get(), 0.0F);
            }
        }

        return 0.0F;
    }

    public static BiomeSpecialEffects getSpecialEffects(Biome biome) {
        if (biome == null) {
            return null;
        }

        var ext = asExtended(biome);
        if (ext != null) {
            try {
                return ext.dsurround_getSpecialEffects();
            } catch (LinkageError | RuntimeException ignored) {
                // Fall through to public/reflected access.
            }
        }

        Object effects = invokeAny(biome, "getModifiedSpecialEffects", "getSpecialEffects", "specialEffects")
                .or(() -> readField(biome, "specialEffects", "effects"))
                .orElse(null);
        return effects instanceof BiomeSpecialEffects specialEffects ? specialEffects : null;
    }

    private static IBiomeExtended asExtended(Biome biome) {
        // The 26.x Biome class is not statically related to our mixin interface at
        // compile time.  Cast through Object so javac allows the runtime mixin
        // interface check.
        Object mixedBiome = biome;
        return mixedBiome instanceof IBiomeExtended ext ? ext : null;
    }

    private static Optional<Object> invokeAny(Object target, String... names) {
        if (target == null) {
            return Optional.empty();
        }
        for (String name : names) {
            var method = findMethod(target.getClass(), name);
            if (method == null || method.getParameterCount() != 0) {
                continue;
            }
            try {
                method.setAccessible(true);
                return Optional.ofNullable(method.invoke(target));
            } catch (IllegalAccessException | InvocationTargetException | LinkageError | RuntimeException ignored) {
                // Try next method.
            }
        }
        return Optional.empty();
    }

    private static Optional<Object> invoke(Object target, String name, Class<?>[] parameters, Object... args) {
        if (target == null) {
            return Optional.empty();
        }
        var method = findMethod(target.getClass(), name, parameters);
        if (method == null) {
            return Optional.empty();
        }
        try {
            method.setAccessible(true);
            return Optional.ofNullable(method.invoke(target, args));
        } catch (IllegalAccessException | InvocationTargetException | LinkageError | RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private static Optional<Object> readField(Object target, String... names) {
        if (target == null) {
            return Optional.empty();
        }
        for (String name : names) {
            var field = findField(target.getClass(), name);
            if (field == null) {
                continue;
            }
            try {
                field.setAccessible(true);
                return Optional.ofNullable(field.get(target));
            } catch (IllegalAccessException | LinkageError | RuntimeException ignored) {
                // Try next field.
            }
        }
        return Optional.empty();
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                return current.getDeclaredMethod(name, parameterTypes);
            } catch (NoSuchMethodException ignored) {
                // Keep walking.
            }
        }
        for (Class<?> iface : type.getInterfaces()) {
            try {
                return iface.getDeclaredMethod(name, parameterTypes);
            } catch (NoSuchMethodException ignored) {
                // Keep trying.
            }
        }
        return null;
    }

    private static Field findField(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // Keep walking.
            }
        }
        return null;
    }

    private static float asFloat(Object value, float fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number n) {
            return n.floatValue();
        }
        try {
            return Float.parseFloat(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
