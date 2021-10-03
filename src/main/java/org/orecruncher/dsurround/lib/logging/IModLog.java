package org.orecruncher.dsurround.lib.logging;

import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

@SuppressWarnings("unused")
public interface IModLog {

    default void info(final String msg, @Nullable final Object... parms) {
    }

    default void info(final Supplier<String> message) {
        info(message.get());
    }

    default void warn(final String msg, @Nullable final Object... parms) {
    }

    default void warn(final Supplier<String> message) {
        warn(message.get());
    }

    default void debug(final String msg, @Nullable final Object... parms) {
    }

    default void debug(final Supplier<String> message) {
        debug(message.get());
    }

    default void debug(final int mask, final String msg, @Nullable final Object... parms) {
    }

    default void debug(final int mask, final Supplier<String> message) {
        debug(message.get());
    }

    default void error(final Throwable e, final String msg, @Nullable final Object... parms) {
    }

    default void error(final Throwable e, final Supplier<String> message) {
        error(e, message.get());
    }
}