package org.orecruncher.dsurround.lib.scripting;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.lib.scripting.engine.ScriptException;
import org.orecruncher.dsurround.lib.scripting.engine.ScriptHelpers;

/**
 * The type of a function parameter. The engine converts each argument to the parameter's type before calling the
 * function, so handlers receive values that are already the right type.
 * <p>
 * When an argument is a constant (a literal in the script), it is converted once when the script is compiled. A
 * value that cannot be converted is then reported as a compile error pointing at the argument, so a typo such as
 * {@code biome.is('HOTT')} fails when the script is loaded instead of silently returning false every tick.
 * Arguments that are not constant are converted each time the function is called.
 *
 * @param <T> Java type that handlers receive for this parameter
 */
public final class ArgType<T> {

    /**
     * Converts a script value to the parameter's type.
     *
     * @param <T> Java type produced
     */
    @FunctionalInterface
    public interface Converter<T> {
        /**
         * @param value Value produced by the script (may be null)
         * @return The converted value, or null if the value is not valid for this type. To report a more specific
         * problem, call {@link ArgType#reject(String)} instead of returning null.
         */
        @Nullable T convert(@Nullable Object value);
    }

    /** Any value, passed through unchanged (including null). */
    public static final ArgType<Object> ANY = new ArgType<>("value", v -> v, true);
    /** A number, as a Double. Numeric strings are accepted. */
    public static final ArgType<Double> NUMBER = of("number", v -> v instanceof Double d ? d : ScriptHelpers.tryToDouble(v));
    /** A whole number that fits in an int, as an Integer. */
    public static final ArgType<Integer> INTEGER = of("whole number", ScriptHelpers::tryToInteger);
    /** A boolean, using the script's boolean conversion (numbers are true when non-zero, strings when "true"). */
    public static final ArgType<Boolean> BOOLEAN = of("boolean", ScriptHelpers::tryToBoolean);
    /** A string. Any value is accepted and converted the same way string concatenation does. */
    public static final ArgType<String> STRING = of("string", ScriptHelpers::toStringValue);

    private final String name;
    private final Converter<T> converter;
    private final boolean passThrough;

    private ArgType(String name, Converter<T> converter, boolean passThrough) {
        this.name = name;
        this.converter = converter;
        this.passThrough = passThrough;
    }

    /**
     * Creates a parameter type.
     *
     * @param name      Name used in error messages, as in "argument 2 must be a {name}"
     * @param converter Converts a script value, returning null when the value is not valid
     */
    public static <T> ArgType<T> of(@NotNull String name, @NotNull Converter<T> converter) {
        return new ArgType<>(name, converter, false);
    }

    /**
     * Called from a {@link Converter} to reject a value with a specific reason, such as "unknown biome trait 'HOTT'".
     * The engine adds the function name and the argument's location to the message.
     */
    public static <T> T reject(@NotNull String reason) {
        ScriptException.throwException(reason);
        return null;
    }

    public String name() {
        return this.name;
    }

    /**
     * @return True if every value, including null, is accepted unchanged.
     */
    public boolean isPassThrough() {
        return this.passThrough;
    }

    /**
     * Converts a value for this parameter. Used by the engine.
     *
     * @return The converted value, or null if it is not valid (unless the type is pass-through).
     * @throws ScriptException When the converter rejects the value with a specific reason.
     */
    public @Nullable T convert(@Nullable Object value) {
        return this.converter.convert(value);
    }

    @Override
    public String toString() {
        return this.name;
    }
}
