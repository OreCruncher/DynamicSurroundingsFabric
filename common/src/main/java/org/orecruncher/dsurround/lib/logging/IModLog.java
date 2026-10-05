package org.orecruncher.dsurround.lib.logging;

import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * The mod's logger. Messages use {@link String#format} syntax when arguments are given, and are logged as is when
 * not (so a message without arguments may contain '%'). Supplier versions only build the message when it will be
 * logged.
 * <p>
 * Implementations provide {@link #log}, {@link #isDebugging} and {@link #isTracing}; everything else goes through
 * {@code log}. Logging must never throw.
 */
public interface IModLog {

    enum Level {
        DEBUG, INFO, WARN, ERROR
    }

    /**
     * Whether debug logging is enabled.
     */
    boolean isDebugging();

    /**
     * Whether debug logging is enabled and any of the bits in {@code mask} are set in the trace mask.
     */
    boolean isTracing(int mask);

    /**
     * Logs a message. Every other method comes here.
     *
     * @param level  the level; DEBUG messages are only passed in when they should be logged
     * @param t      the exception to include, if any
     * @param format the message, formatted with {@code params} if there are any
     */
    void log(Level level, @Nullable Throwable t, String format, @Nullable Object... params);

    default void info(final String msg, @Nullable final Object... params) {
        this.log(Level.INFO, null, msg, params);
    }

    default void info(final Supplier<String> message) {
        this.log(Level.INFO, null, message.get());
    }

    default void warn(final String msg, @Nullable final Object... params) {
        this.log(Level.WARN, null, msg, params);
    }

    default void warn(final Supplier<String> message) {
        this.log(Level.WARN, null, message.get());
    }

    /**
     * A warning that comes with an exception: something went wrong, but was recovered from.
     */
    default void warn(final Throwable e, final String msg, @Nullable final Object... params) {
        this.log(Level.WARN, e, msg, params);
    }

    default void debug(final String msg, @Nullable final Object... params) {
        if (this.isDebugging())
            this.log(Level.DEBUG, null, msg, params);
    }

    default void debug(final Supplier<String> message) {
        if (this.isDebugging())
            this.log(Level.DEBUG, null, message.get());
    }

    /**
     * Logs if debugging and any of the bits in {@code mask} are set in the trace mask.
     */
    default void debug(final int mask, final String msg, @Nullable final Object... params) {
        if (this.isTracing(mask))
            this.log(Level.DEBUG, null, msg, params);
    }

    /**
     * Logs if debugging and any of the bits in {@code mask} are set in the trace mask. The message is only built
     * if so.
     */
    default void debug(final int mask, final Supplier<String> message) {
        if (this.isTracing(mask))
            this.log(Level.DEBUG, null, message.get());
    }

    default void error(final Throwable e, final String msg, @Nullable final Object... params) {
        this.log(Level.ERROR, e, msg, params);
    }

    default void error(final Throwable e, final Supplier<String> message) {
        this.log(Level.ERROR, e, message.get());
    }
}
