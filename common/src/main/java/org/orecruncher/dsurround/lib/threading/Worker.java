package org.orecruncher.dsurround.lib.threading;

import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.math.LoggingTimerEMA;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs a task repeatedly on its own thread, every {@code frequencyMsecs} milliseconds. If a run takes longer than
 * that, the next starts as soon as it finishes; runs never overlap. A run that throws is logged, and later runs
 * still happen.
 * <p>
 * A worker runs once: after {@link #stop()} it can't be started again; create a new one.
 */
public final class Worker {

    static final String THREAD_PREFIX = "dsurround-";

    /**
     * How long {@link #stop()} waits for a run in progress to finish.
     */
    static final long STOP_TIMEOUT_MS = 1000;

    private final String name;
    private final Runnable task;
    private final IModLog logger;
    private final int frequency;
    private final LoggingTimerEMA timeTrack;
    private final ScheduledExecutorService executorService;
    private boolean started;

    // Written by the worker thread, read when diagnostics are shown
    private volatile String timing = "";
    private volatile long lastRunMsecs;

    /**
     * Instantiates a worker thread to execute a task on a repeating basis.
     *
     * @param name           Name of the worker; its thread is named after it
     * @param task           The task to be executed
     * @param frequencyMsecs The frequency of execution in msecs
     * @param logger         The logger to use when logging is needed
     */
    public Worker(final String name, final Runnable task, final int frequencyMsecs, final IModLog logger) {
        this.name = name;
        this.task = task;
        this.frequency = frequencyMsecs;
        this.executorService = Executors.newSingleThreadScheduledExecutor(threadFactory(name));
        this.timeTrack = new LoggingTimerEMA(this.name);
        this.logger = logger;
    }

    /**
     * A factory for daemon threads named "dsurround-{name}", numbered if there is more than one. Daemon, so they
     * can't keep the game's process alive after it exits; named, so they can be identified in thread dumps,
     * profilers and crash reports.
     */
    public static ThreadFactory threadFactory(String name) {
        var count = new AtomicInteger();
        return runnable -> {
            int n = count.incrementAndGet();
            var thread = new Thread(runnable, THREAD_PREFIX + name + (n == 1 ? "" : "-" + n));
            thread.setDaemon(true);
            return thread;
        };
    }

    private void run() {
        this.timeTrack.begin();
        try {
            this.task.run();
        } catch (final Throwable t) {
            this.logger.error(t, "Error processing %s!", this.name);
        }
        this.timeTrack.end();
        this.lastRunMsecs = this.timeTrack.getLastSampleMSecs();
        this.timing = this.timeTrack.toString();
    }

    /**
     * Starts up the worker.  Execution will start immediately.
     *
     * @throws IllegalStateException if it was already started, or has been stopped
     */
    public synchronized void start() {
        if (this.executorService.isShutdown())
            throw new IllegalStateException(String.format("Worker '%s' has been stopped; create a new one", this.name));
        if (this.started)
            throw new IllegalStateException(String.format("Worker '%s' is already started", this.name));
        this.started = true;
        this.executorService.scheduleAtFixedRate(this::run, 0, this.frequency, TimeUnit.MILLISECONDS);
    }

    /**
     * Stops the worker, waiting up to a second for a run in progress to finish, so the caller can then safely
     * release what the task uses.
     *
     * @return true if it stopped in time; false if the run had to be interrupted
     */
    public boolean stop() {
        return this.stop(STOP_TIMEOUT_MS);
    }

    boolean stop(long timeoutMs) {
        this.executorService.shutdown();
        try {
            if (this.executorService.awaitTermination(timeoutMs, TimeUnit.MILLISECONDS))
                return true;
            this.logger.warn("Worker '%s' didn't finish within %dms of being stopped; interrupting it", this.name, timeoutMs);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        this.executorService.shutdownNow();
        return false;
    }

    /**
     * Gathers a diagnostic string to display or log.
     *
     * @return String for logging or display
     */
    public String getDiagnosticString() {
        var timing = this.timing;
        if (timing.isEmpty())
            return "";
        long sleepTime = this.frequency - this.lastRunMsecs;
        var text = "%s (idle for %dmsecs)".formatted(timing, Math.max(sleepTime, 0));
        if (sleepTime < 0)
            text += "; running behind %dms".formatted(-sleepTime);
        return text;
    }
}
