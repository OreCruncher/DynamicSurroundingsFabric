package org.orecruncher.dsurround.lib.threading;

import org.orecruncher.dsurround.lib.logging.LogThrottle;

import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Helpers for waiting on tasks handed to a thread pool.
 */
public final class Tasks {

    private Tasks() {
    }

    /**
     * Waits for each task in turn. When the tasks run side by side, by the time the first is done most of the rest
     * are too.
     * <p>
     * A task that failed is logged through {@code errors} (which limits how often), rather than lost. If the waiting
     * thread is interrupted, which is how a worker is stopped when a run overruns, it stops waiting and keeps the
     * interrupt set, leaving the tasks to finish on their own.
     *
     * @param what names the tasks, for the log
     * @return false if interrupted
     */
    public static boolean awaitAll(Collection<? extends Future<?>> tasks, LogThrottle<Object> errors, String what) {
        for (var task : tasks) {
            try {
                task.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            } catch (ExecutionException e) {
                errors.error(e.getCause(), "%s failed", what);
            }
        }
        return true;
    }
}
