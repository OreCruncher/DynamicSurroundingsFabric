package org.orecruncher.dsurround.lib.logging;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * {@link IModLog} over an SLF4J logger.
 * <p>
 * Each line of a multi-line message is logged as its own record. An exception is logged with the last line of its
 * message, so the two stay together. Debug messages are logged at INFO, so they appear with Minecraft's default log
 * settings, prefixed "[DEBUG]".
 * <p>
 * Whether debug messages are logged, and which trace bits are on, is global to the mod: set through
 * {@link #setDebug} and {@link #setTraceMask}.
 */
public final class ModLog implements IModLog {

    static final String DEBUG_PREFIX = "[DEBUG] ";

    // Written on the client thread when the configuration changes, read on any thread that logs
    private static volatile boolean debugging;
    private static volatile int traceMask;

    private final Logger logger;

    ModLog(Logger logger) {
        this.logger = logger;
    }

    public static IModLog create(String modId) {
        return new ModLog(LoggerFactory.getLogger(Objects.requireNonNull(modId)));
    }

    /**
     * A logger named after the parent, e.g. "dsurround/EventingFactory". For any other kind of IModLog (a test
     * double, say) the parent itself is returned.
     */
    public static IModLog createChild(IModLog parent, String childName) {
        if (parent instanceof ModLog ml)
            return create(ml.logger.getName() + "/" + childName);
        return parent;
    }

    /**
     * Turns debug logging on or off for the whole mod.
     */
    public static void setDebug(final boolean flag) {
        debugging = flag;
    }

    /**
     * Sets which debug traces are on, for the whole mod.
     */
    public static void setTraceMask(final int mask) {
        traceMask = mask;
    }

    String getName() {
        return this.logger.getName();
    }

    @Override
    public boolean isDebugging() {
        return debugging;
    }

    @Override
    public boolean isTracing(int mask) {
        return debugging && (traceMask & mask) != 0;
    }

    @Override
    public void log(Level level, @Nullable Throwable t, String format, @Nullable Object... params) {
        try {
            var lines = format(format, params).lines().toList();
            if (lines.isEmpty())
                lines = List.of("");

            for (int i = 0; i < lines.size(); i++) {
                var line = level == Level.DEBUG ? DEBUG_PREFIX + lines.get(i) : lines.get(i);
                // The exception goes with the last line, so it is in the same record as (the end of) its message
                var throwable = i == lines.size() - 1 ? t : null;
                switch (level) {
                    case DEBUG, INFO -> this.logger.info(line, throwable);
                    case WARN -> this.logger.warn(line, throwable);
                    case ERROR -> this.logger.error(line, throwable);
                }
            }
        } catch (RuntimeException e) {
            // Logging must never fail the caller
            this.logger.error("Unable to log message: {}", format, e);
        }
    }

    /**
     * The message, formatted with the parameters if there are any. A format that doesn't match its arguments (or
     * an argument whose toString throws) gives the format and the arguments as they are, rather than an exception.
     */
    static String format(String format, @Nullable Object... params) {
        if (params == null || params.length == 0)
            return format;
        try {
            return String.format(format, params);
        } catch (RuntimeException e) {
            return format + " [format error " + e.getClass().getSimpleName() + ": " + e.getMessage()
                    + "; arguments " + describe(params) + "]";
        }
    }

    private static String describe(Object[] params) {
        try {
            return Arrays.toString(params);
        } catch (RuntimeException e) {
            return params.length + " argument(s)";
        }
    }
}
