package org.orecruncher.dsurround.lib.reflection;

import com.google.common.base.Suppliers;
import it.unimi.dsi.fastutil.Pair;
import me.shedaniel.cloth.clothconfig.shadowed.blue.endless.jankson.annotation.NonnullByDefault;
import net.minecraft.ReportedException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.NonnullDefault;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.collections.ListMap;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.logging.ModLog;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public final class ReflectionHelper {

    private static final Supplier<IModLog> LOGGER = Suppliers.memoize(() -> ModLog.createChild(Library.LOGGER, "ReflectionHelper"));
    private static final Map<Class<?>, CacheData>  cacheData = new IdentityHashMap<>(8);

    public static Optional<Method> findMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        return findMethod(type, new String[]{name}, parameterTypes);
    }

    public static Optional<Method> findMethod(@NotNull Class<?> type, @NotNull String[] names, @Nullable Class<?>... parameterTypes) {
        Objects.requireNonNull(type, "type");
        if (names == null || names.length == 0)
            return Optional.empty();

        // Prefer the first name in the list for caching. It's possible that a method was identified previously by
        // obfuscated name rather than mapped name.
        var preferredName = names[0];

        var data = getCacheData(type);
        if (data.methods.containsKey(preferredName)) {
            return Optional.ofNullable(data.methods.get(preferredName));
        }

        for (String name : names) {
            for (Class<?> current = type; current != null; current = current.getSuperclass()) {
                var result = resolveMethod(current, name, parameterTypes);
                if (result != null) {
                    data.methods.put(preferredName, result);
                    return Optional.of(result);
                }
            }
        }

        for (String name : names) {
            for (Class<?> xface : type.getInterfaces()) {
                var result = resolveMethod(xface, name, parameterTypes);
                if (result != null) {
                    data.methods.put(preferredName, result);
                    return Optional.of(result);
                }
            }
        }

        data.methods.put(preferredName, null);
        return Optional.empty();
    }

    public static Optional<Field> findField(Class<?> type, String name) {
        return findField(type, new String[]{name});
    }

    public static Optional<Field> findField(Class<?> type, String[] names) {
        Objects.requireNonNull(type, "type");
        if (names == null || names.length == 0)
            return Optional.empty();

        // Prefer the first name in the list for caching. It's possible that a field was identified previously by
        // obfuscated name rather than mapped name.
        var preferredName = names[0];

        var data = getCacheData(type);
        if (data.fields.containsKey(preferredName)) {
            return Optional.ofNullable(data.fields.get(preferredName));
        }

        for (String name : names) {
            for (Class<?> current = type; current != null; current = current.getSuperclass()) {
                var result = resolveField(current, name);
                if (result != null) {
                    data.fields.put(preferredName, result);
                    return Optional.of(result);
                }
            }
        }

        data.fields.put(preferredName, null);
        return Optional.empty();
    }

    public static float asFloat(Object value, float fallback) {
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

    @SuppressWarnings("unchecked")
    public static <T> Optional<T> cast(@Nullable Object value, @NotNull Class<T> desiredType) {
        try {
            if (desiredType.isInstance(value)) {
                return Optional.of((T) value);
            }
        } catch (LinkageError | RuntimeException ignored) {
        }
        return Optional.empty();
    }

    /**
     * Given a list of lambdas, this routine will iteratively call each until finding one that success without
     * exception. It will return that lambda as well as the result. Main purpose is in support of mixin environments
     * where binding errors can occur, and the logic would need to fallback on classic reflection. Note that the
     * resulting lambda can be cached and reused as an optimization.
     */
    @SafeVarargs
    public static <P1, R> @NotNull Pair<Function<P1, R>, R> choose(String description, P1 parameter1, Function<P1, R>... choices) {
        for (int i = 0; i < choices.length; i++) {
            try {
                var choice = choices[i];
                var result = choice.apply(parameter1);
                LOGGER.get().info("[%s] Selected choice %d", description, i + 1);
                return Pair.of(choice, result);
            } catch (Throwable ignored) {
            }
        }

        throw new RuntimeException("ReflectionHelper: [%s] Exhausted all %d choices".formatted(description, choices.length));
    }

    /**
     * Given a list of lambdas, this routine will iteratively call each until finding one that success without
     * exception. It will return that lambda as well as the result. Main purpose is in support of mixin environments
     * where binding errors can occur, and the logic would need to fallback on classic reflection. Note that the
     * resulting lambda can be cached and reused as an optimization.
     */
    @SafeVarargs
    public static <P1, P2, R> @NotNull Pair<BiFunction<P1, P2, R>, R> choose(String description, P1 parameter1, P2 parameter2, BiFunction<P1, P2, R>... choices) {
        for (int i = 0; i < choices.length; i++) {
            try {
                var choice = choices[i];
                var result = choice.apply(parameter1, parameter2);
                LOGGER.get().info("[%s] Selected choice %d", description, i + 1);
                return Pair.of(choice, result);
            } catch (LinkageError | RuntimeException ignored) {
            }
        }

        throw new RuntimeException("ReflectionHelper: [%s] Exhausted all %d choices".formatted(description, choices.length));
    }

    @Nullable
    private static Method resolveMethod(Class<?> clazz, String name, Class<?>... parameterTypes ) {
        try {
            var method = clazz.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (LinkageError | RuntimeException | NoSuchMethodException ignored) {
        }
        return null;
    }

    private static Field resolveField(Class<?> clazz, String name) {
        try {
            var field = clazz.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (LinkageError | RuntimeException | NoSuchFieldException ignored) {
        }
        return null;
    }

    private static CacheData getCacheData(Class<?> clazz) {
        var data = cacheData.get(clazz);
        if (data == null) {
            data = new CacheData(clazz);
            cacheData.put(clazz, data);
        }
        return data;
    }

    private static class CacheData {
        public final Class<?> clazz;
        public final Map<String, Method> methods;
        public final Map<String, Field> fields;

        public CacheData(Class<?> clazz) {
            this.clazz = clazz;
            this.methods = new ListMap<>();
            this.fields = new ListMap<>();
        }
    }
}
