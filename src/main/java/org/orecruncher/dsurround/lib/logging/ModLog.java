package org.orecruncher.dsurround.lib.logging;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public final class ModLog implements IModLog {

    private static final Pattern REGEX_SPLIT = Pattern.compile("\\n");

    private final Marker marker;
    private final Logger logger;

    private boolean debugging;
    private int traceMask;

    public ModLog(final Class<?> clazz) {
        this(Objects.requireNonNull(clazz).getSimpleName());
    }

    public ModLog(final String name) {
        this.logger = LogManager.getLogger(Objects.requireNonNull(name));
        this.marker = MarkerManager.getMarker("MOD");
    }

    private static void outputLines(@Nullable final Marker marker, final ILoggit out, final String format, @Nullable final Object... parms) {
        for (final String l : REGEX_SPLIT.split(String.format(format, parms)))
            out.log(marker, l);
    }

    public IModLog createChild(final Class<?> child) {
        return new ChildLog(this, Objects.requireNonNull(child));
    }

    public void setDebug(final boolean flag) {
        this.debugging = flag;
    }

    public void setTraceMask(final int mask) {
        this.traceMask = mask;
    }

    public boolean testTrace(final int mask) {
        return (this.traceMask & mask) != 0;
    }

    public boolean isDebugging() {
        return this.debugging;
    }

    @Override
    public void info(final String msg, @Nullable final Object... parms) {
        info(this.marker, msg, parms);
    }

    private void info(final Marker marker, final String msg, @Nullable final Object... parms) {
        outputLines(marker, logger::info, msg, parms);
    }

    @Override
    public void info(final Supplier<String> message) {
        info(this.marker, message);
    }

    private void info(final Marker marker, final Supplier<String> message) {
        outputLines(marker, logger::info, message.get());
    }

    @Override
    public void warn(final String msg, @Nullable final Object... parms) {
        warn(this.marker, msg, parms);
    }

    private void warn(final Marker marker, final String msg, @Nullable final Object... parms) {
        outputLines(marker, logger::warn, msg, parms);
    }

    @Override
    public void warn(final Supplier<String> message) {
        warn(this.marker, message);
    }

    private void warn(final Marker marker, final Supplier<String> message) {
        outputLines(marker, logger::warn, message.get());
    }

    @Override
    public void debug(final String msg, @Nullable final Object... parms) {
        debug(this.marker, msg, parms);
    }

    private void debug(final Marker marker, final String msg, @Nullable final Object... parms) {
        if (isDebugging())
            outputLines(marker, logger::info, msg, parms);
    }

    @Override
    public void debug(final Supplier<String> message) {
        debug(this.marker, message);
    }

    private void debug(final Marker marker, final Supplier<String> message) {
        if (isDebugging())
            outputLines(marker, logger::info, message.get());
    }

    @Override
    public void debug(final int mask, final String msg, @Nullable final Object... parms) {
        debug(this.marker, msg, parms);
    }

    private void debug(final Marker marker, final int mask, final String msg, @Nullable final Object... parms) {
        if (isDebugging() && testTrace(mask))
            outputLines(marker, logger::info, msg, parms);
    }

    @Override
    public void debug(final int mask, final Supplier<String> message) {
        debug(this.marker, message);
    }

    private void debug(final Marker marker, final int mask, final Supplier<String> message) {
        if (isDebugging() && testTrace(mask))
            outputLines(marker, logger::info, message.get());
    }

    @Override
    public void error(final Throwable e, final String msg, @Nullable final Object... parms) {
        error(this.marker, e, msg, parms);
    }

    private void error(final Marker marker, final Throwable e, final String msg, @Nullable final Object... parms) {
        outputLines(marker, logger::error, msg, parms);
        e.printStackTrace();
    }

    @Override
    public void error(final Throwable e, final Supplier<String> message) {
        error(this.marker, e, message);
    }

    private void error(final Marker marker, final Throwable e, final Supplier<String> message) {
        error(marker, e, message.get());
    }

    @FunctionalInterface
    private interface ILoggit {
        void log(Marker m, String s, Object... params);
    }

    private static class ChildLog implements IModLog {

        private final ModLog parent;
        private final Marker marker;

        ChildLog(final ModLog parent, final Class<?> child) {
            this.parent = parent;
            this.marker = MarkerManager.getMarker(child.getSimpleName());
        }

        @Override
        public void info(String msg, @Nullable Object... parms) {
            this.parent.info(this.marker, msg, parms);
        }

        @Override
        public void info(Supplier<String> message) {
            this.parent.info(this.marker, message);
        }

        @Override
        public void warn(String msg, @Nullable Object... parms) {
            this.parent.warn(this.marker, msg, parms);
        }

        @Override
        public void warn(Supplier<String> message) {
            this.parent.warn(this.marker, message);
        }

        @Override
        public void debug(String msg, @Nullable Object... parms) {
            this.parent.debug(this.marker, msg, parms);
        }

        @Override
        public void debug(Supplier<String> message) {
            this.parent.debug(this.marker, message);
        }

        @Override
        public void debug(int mask, String msg, @Nullable Object... parms) {
            this.parent.debug(this.marker, mask, msg, parms);
        }

        @Override
        public void debug(int mask, Supplier<String> message) {
            this.parent.debug(this.marker, mask, message);
        }

        @Override
        public void error(Throwable e, String msg, @Nullable Object... parms) {
            this.parent.error(this.marker, e, msg, parms);
        }

        @Override
        public void error(Throwable e, Supplier<String> message) {
            this.parent.error(this.marker, e, message);
        }
    }
}