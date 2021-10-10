package org.orecruncher.dsurround.lib;

import com.google.common.base.Preconditions;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.lib.logging.IModLog;

/**
 * Logic that supports guarded operations.  Exceptions encountered will be logged.
 */
@SuppressWarnings("unused")
public final class Guard {

    private static final IModLog LOGGER = Client.LOGGER.createChild(Guard.class);

    /**
     * Executes the Runnable.  Exceptions are logged to the mod's logger and then suppressed.
     *
     * @param process Process to execute
     */
    public static void execute(Runnable process) {
        executeImpl(process, LOGGER, false);
    }

    /**
     * Executes the Runnable.  Exceptions are logged to the mod's logger and then rethrown.
     *
     * @param process Process to execute
     */
    public static void executeWithThrow(Runnable process) {
        executeImpl(process, LOGGER, true);
    }

    /**
     * Executes the Runnable.  Exceptions are logged to the mod's logger and then suppressed.
     *
     * @param process Process to execute
     */
    public static void execute(Runnable process, IModLog logger) {
        executeImpl(process, logger, false);
    }

    /**
     * Executes the Runnable.  Exceptions are logged to the mod's logger and then rethrown.
     *
     * @param process Process to execute
     */
    public static void executeWithThrow(Runnable process, IModLog logger) {
        executeImpl(process, logger, true);
    }

    /**
     * Executes the Runnable.  Exceptions are logged to the specified logger and then suppressed.
     *
     * @param process Process to execute
     * @param logger  Logger to emit trace information if needed
     */
    private static void executeImpl(Runnable process, IModLog logger, boolean rethrow) {
        Preconditions.checkNotNull(process);
        Preconditions.checkNotNull(logger);

        try {
            process.run();
        } catch (Throwable t) {
            logger.error(t, "Error processing request");
            if (rethrow)
                throw t;
        }
    }
}
