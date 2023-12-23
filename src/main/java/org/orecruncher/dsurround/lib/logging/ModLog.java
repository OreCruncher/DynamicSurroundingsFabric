package org.orecruncher.dsurround.lib.logging;

import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.lib.infra.IMinecraftMod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public final class ModLog implements IModLog {

    private static final Pattern REGEX_SPLIT = Pattern.compile("\\n");

    private final Logger logger;
    private boolean debugging;
    private int traceMask;

    public ModLog(IMinecraftMod mod) {
        this(mod.get_modId());
    }

    public ModLog(String modId) {
        this.logger = LoggerFactory.getLogger(Objects.requireNonNull(modId));
    }

    private static void outputLines(final Consumer<String> out, final String format, @Nullable final Object... params) {
        String output = format;
        if (params != null && params.length > 0)
            output = String.format(format, params);
        REGEX_SPLIT.splitAsStream(output).forEach(out);
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
    public void info(final String msg, @Nullable final Object... params) {
        outputLines(this.logger::info, msg, params);
    }

    @Override
    public void info(final Supplier<String> message) {
        this.info(message.get());
    }

    @Override
    public void warn(final String msg, @Nullable final Object... params) {
        outputLines(this.logger::warn, msg, params);
    }

    @Override
    public void warn(final Supplier<String> message) {
        this.warn(message.get());
    }

    @Override
    public void debug(final String msg, @Nullable final Object... params) {
        if (isDebugging())
            outputLines(this.logger::info, msg, params);
    }

    @Override
    public void debug(final Supplier<String> message) {
        if (isDebugging())
            this.debug(message.get());
    }

    @Override
    public void debug(final int mask, final String msg, @Nullable final Object... params) {
        if (isDebugging() && testTrace(mask))
            outputLines(this.logger::info, msg, params);
    }

    @Override
    public void debug(final int mask, final Supplier<String> message) {
        if (isDebugging())
            this.debug(mask, message.get());
    }

    @Override
    public void error(final Throwable e, final String msg, @Nullable final Object... params) {
        outputLines(this.logger::error, msg, params);
        this.logger.error(e.toString());
    }

    @Override
    public void error(final Throwable e, final Supplier<String> message) {
        this.error(e, message.get());
    }
}