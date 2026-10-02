package org.orecruncher.dsurround.lib.reflection;

import com.google.common.base.Suppliers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.collections.Pair;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.logging.ModLog;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public final class ReflectionHelper {

    private static final Supplier<IModLog> LOGGER = Suppliers.memoize(() -> ModLog.createChild(Library.LOGGER, "ReflectionHelper"));

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
}
