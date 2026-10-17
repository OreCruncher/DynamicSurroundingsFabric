package org.orecruncher.dsurround.lib.function;

import com.google.common.base.Preconditions;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.logging.LogThrottle;

import java.util.function.Supplier;

/**
 * Runs code whose failure should be logged and survived rather than thrown on: a mixin's body inside the game's frame
 * or tick, say, where the game carries on as it would without the mod. Failures are logged up to a limit, which
 * {@link #reset} lifts again (on a reload, say). The code keeps running after a failure, so something that failed
 * once (a world unloading, say) carries on working. Errors the JVM can't recover from (running out of memory, say)
 * are passed on rather than caught. Thread-safe.
 */
public final class Guard {

    /**
     * Logs every failure: for code whose failures are few and each worth seeing
     */
    public static final int UNLIMITED = Integer.MAX_VALUE;

    private static final IModLog LOGGER = ContainerManager.memoize(IModLog.class);
    // Failures logged by default; after that they are counted and dropped until reset
    private static final int LOG_LIMIT = 10;

    private final String name;
    private final LogThrottle<Object> errors;

    private Guard(String name, IModLog logger, int limit) {
        this.name = name;
        // No "until" in the notice: most guards are never reset, and those that are say when in their own code
        this.errors = LogThrottle.firstN(logger, name + " errors", null, limit);
    }

    /**
     * A guard logging to the mod's logger.
     *
     * @param name what is guarded, for the log
     */
    public static Guard named(String name) {
        return named(name, LOGGER);
    }

    /**
     * A guard logging to the given logger.
     *
     * @param name   what is guarded, for the log
     * @param logger where failures are logged
     */
    public static Guard named(String name, IModLog logger) {
        return named(name, logger, LOG_LIMIT);
    }

    /**
     * A guard logging to the given logger, up to a limit.
     *
     * @param name   what is guarded, for the log
     * @param logger where failures are logged
     * @param limit  the most failures logged until {@link #reset}, or {@link #UNLIMITED}
     */
    public static Guard named(String name, IModLog logger, int limit) {
        return new Guard(Preconditions.checkNotNull(name), Preconditions.checkNotNull(logger), limit);
    }

    /**
     * The body's value, or the fallback if it throws.
     */
    public <T> T get(Supplier<T> body, T fallback) {
        try {
            return body.get();
        } catch (Throwable t) {
            this.failed(t);
            return fallback;
        }
    }

    /**
     * Runs the body.
     *
     * @return true if it finished, false if it threw
     */
    public boolean run(Runnable body) {
        try {
            body.run();
            return true;
        } catch (Throwable t) {
            this.failed(t);
            return false;
        }
    }

    /**
     * Runs the body for something, named in the log if it fails.
     *
     * @param subject what the body was run for (a biome, say)
     * @return true if it finished, false if it threw
     */
    public boolean run(Runnable body, Object subject) {
        try {
            body.run();
            return true;
        } catch (Throwable t) {
            this.failed(t, subject);
            return false;
        }
    }

    /**
     * Logs a failure caught by the caller, for code run too often to wrap in a lambda (each particle, each tick).
     * Rethrows errors the JVM can't recover from.
     */
    public void failed(Throwable t) {
        if (t instanceof VirtualMachineError fatal)
            throw fatal;
        this.errors.error(t, "%s failed; the game carries on without it", this.name);
    }

    /**
     * Logs a failure for something, caught by the caller. Rethrows errors the JVM can't recover from.
     *
     * @param subject what failed (a biome, say)
     */
    public void failed(Throwable t, Object subject) {
        if (t instanceof VirtualMachineError fatal)
            throw fatal;
        this.errors.error(t, "%s failed for %s", this.name, subject);
    }

    /**
     * Logs failures again, up to the limit
     */
    public void reset() {
        this.errors.reset();
    }
}
