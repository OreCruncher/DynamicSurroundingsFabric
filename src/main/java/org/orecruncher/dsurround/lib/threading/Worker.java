package org.orecruncher.dsurround.lib.threading;

import net.minecraft.util.math.MathHelper;
import org.apache.commons.lang3.StringUtils;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.math.LoggingTimerEMA;

import java.util.function.Supplier;

public final class Worker {

    private final Thread thread;
    private final Runnable task;
    private final IModLog logger;
    private final int frequency;

    private Supplier<String> diagnosticString;
    private boolean stopProcessing;

    /**
     * Instantiates a worker thread to execute a task on a repeating basis.
     *
     * @param threadName     Name of the worker thread
     * @param task           The task to be executed
     * @param frequencyMsecs The frequency of execution in msecs
     * @param logger         The logger to use when logging is needed
     */
    public Worker(final String threadName, final Runnable task, final int frequencyMsecs, final IModLog logger) {
        this.thread = new Thread(this::run);
        this.thread.setName(threadName);
        this.thread.setDaemon(true);
        this.task = task;
        this.frequency = frequencyMsecs;
        this.logger = logger;
        this.diagnosticString = () -> StringUtils.EMPTY;
    }

    private void run() {
        final LoggingTimerEMA timeTrack = new LoggingTimerEMA(this.thread.getName());
        while (!this.stopProcessing) {
            timeTrack.begin();
            try {
                task.run();
            } catch (final Throwable t) {
                logger.error(t, "Error processing %s!", this.thread.getName());
            }
            timeTrack.end();
            long sleepTime = this.frequency - timeTrack.getLastSampleMSecs();
            long idleTime = MathHelper.clamp(sleepTime, 0, Long.MAX_VALUE);
            var track = timeTrack.toString();
            this.diagnosticString = () -> String.format("%s (idle for %dmsecs)", track, idleTime);
            if (sleepTime > 0) {
                try {
                    Thread.sleep(sleepTime);
                } catch (final Throwable ignore) {
                    logger.warn("Terminating thread [%s]", this.thread.getName());
                    return;
                }
            } else if (sleepTime < 0) {
                this.diagnosticString = () -> this.diagnosticString.get() + String.format("; running behind %dms", Math.abs(sleepTime));
            }
        }

    }

    /**
     * Starts up the worker.  Execution will start immediately.
     */
    public void start() {
        this.thread.start();
    }

    public void stop() {
        try {
            this.stopProcessing = true;
            this.thread.join();
        } catch (final Throwable t) {
            logger.warn("Error stopping worker thread [%s]", this.thread.getName());
        }
    }

    /**
     * Gathers a diagnostic string to display or log.
     *
     * @return String for logging or display
     */

    public String getDiagnosticString() {
        return this.diagnosticString.get();
    }
}