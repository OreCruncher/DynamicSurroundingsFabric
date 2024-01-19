package org.orecruncher.dsurround.lib.threading;

import net.minecraft.util.Mth;
import org.apache.commons.lang3.StringUtils;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.math.LoggingTimerEMA;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class Worker {

    private final String name;
    private final Runnable task;
    private final IModLog logger;
    private final int frequency;
    private final LoggingTimerEMA timeTrack;
    private final ScheduledExecutorService executorService;

    private volatile String diagnosticString;

    /**
     * Instantiates a worker thread to execute a task on a repeating basis.
     *
     * @param name              Name of the worker
     * @param task              The task to be executed
     * @param frequencyMsecs    The frequency of execution in msecs
     * @param logger            The logger to use when logging is needed
     */
    public Worker(final String name, final Runnable task, final int frequencyMsecs, final IModLog logger) {
        this.name = name;
        this.task = task;
        this.frequency = frequencyMsecs;
        this.executorService = Executors.newSingleThreadScheduledExecutor();
        this.timeTrack = new LoggingTimerEMA(this.name);
        this.logger = logger;
        this.diagnosticString = StringUtils.EMPTY;
    }

    private void run() {
        this.timeTrack.begin();
        try {
            this.task.run();
        } catch (final Throwable t) {
            this.logger.error(t, "Error processing %s!", this.name);
        }
        this.timeTrack.end();
        long sleepTime = this.frequency - this.timeTrack.getLastSampleMSecs();
        long idleTime = Mth.clamp(sleepTime, 0, Long.MAX_VALUE);
        var track = this.timeTrack.toString();
        var diagText = "%s (idle for %dmsecs)".formatted(track, idleTime);
        if (sleepTime < 0) {
            diagText += "; running behind %dms".formatted(Math.abs(sleepTime));
        }
        this.diagnosticString = diagText;
    }

    /**
     * Starts up the worker.  Execution will start immediately.
     */
    public void start() {
        this.executorService.scheduleAtFixedRate(this::run, 0, this.frequency, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        try {
            if (!this.executorService.isShutdown())
                this.executorService.shutdown();
        } catch (final Throwable t) {
            this.logger.warn("Error stopping worker thread [%s]", this.name);
        }
    }

    /**
     * Gathers a diagnostic string to display or log.
     *
     * @return String for logging or display
     */
    public String getDiagnosticString() {
        return this.diagnosticString;
    }
}