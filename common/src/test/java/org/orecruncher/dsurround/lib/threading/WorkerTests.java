package org.orecruncher.dsurround.lib.threading;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.orecruncher.dsurround.lib.logging.IModLog;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(value = 10, unit = TimeUnit.SECONDS)
public class WorkerTests {

    private final RecordingLog log = new RecordingLog();
    private Worker worker;

    @AfterEach
    void tearDown() {
        if (this.worker != null)
            this.worker.stop();
    }

    private Worker worker(String name, Runnable task, int frequencyMs) {
        this.worker = new Worker(name, task, frequencyMs, this.log);
        return this.worker;
    }

    // ---- Thread ----------------------------------------------------------------------------------------------

    @Test
    void threadIsNamedAndDaemon() throws Exception {
        // Regression: an unnamed ("pool-N-thread-1") non-daemon thread
        var thread = new AtomicReference<Thread>();
        var ran = new CountDownLatch(1);

        this.worker("Sound Processor", () -> {
            thread.compareAndSet(null, Thread.currentThread());
            ran.countDown();
        }, 50).start();

        assertTrue(ran.await(5, TimeUnit.SECONDS));
        assertEquals("dsurround-Sound Processor", thread.get().getName());
        assertTrue(thread.get().isDaemon());
    }

    @Test
    void threadFactoryNumbersExtraThreads() {
        var factory = Worker.threadFactory("Pool");

        var first = factory.newThread(() -> {});
        var second = factory.newThread(() -> {});

        assertEquals("dsurround-Pool", first.getName());
        assertEquals("dsurround-Pool-2", second.getName());
        assertTrue(first.isDaemon() && second.isDaemon());
    }

    // ---- Running ---------------------------------------------------------------------------------------------

    @Test
    void runsRepeatedly() throws Exception {
        var runs = new CountDownLatch(3);

        this.worker("Repeat", runs::countDown, 20).start();

        assertTrue(runs.await(5, TimeUnit.SECONDS));
    }

    @Test
    void exceptionIsLoggedAndLaterRunsStillHappen() throws Exception {
        var runs = new AtomicInteger();
        var later = new CountDownLatch(1);

        this.worker("Flaky", () -> {
            if (runs.incrementAndGet() == 1)
                throw new IllegalStateException("first run fails");
            later.countDown();
        }, 20).start();

        assertTrue(later.await(5, TimeUnit.SECONDS), "a run after the failure");
        var errors = this.log.at(IModLog.Level.ERROR);
        assertEquals("Error processing Flaky!", errors.getFirst().message());
        assertInstanceOf(IllegalStateException.class, errors.getFirst().throwable());
    }

    @Test
    void diagnosticsReportTimingAndIdle() throws Exception {
        var w = this.worker("Diag", () -> {}, 1000);

        assertEquals("", w.getDiagnosticString(), "nothing before the first run");
        w.start();
        // The first run is immediate; wait for its timing to be recorded
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (w.getDiagnosticString().isEmpty() && System.nanoTime() < deadline)
            Thread.onSpinWait();

        var text = w.getDiagnosticString();
        assertTrue(text.startsWith("Diag:"), text);
        assertTrue(text.contains("(idle for "), text);
        assertFalse(text.contains("running behind"), text);
    }

    @Test
    void diagnosticsReportRunningBehind() throws Exception {
        var w = this.worker("Slow", () -> {
            try {
                Thread.sleep(60);
            } catch (InterruptedException ignored) {
            }
        }, 10);
        w.start();

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!w.getDiagnosticString().contains("running behind") && System.nanoTime() < deadline)
            Thread.sleep(10);

        var text = w.getDiagnosticString();
        assertTrue(text.contains("(idle for 0msecs); running behind"), text);
    }

    // ---- Starting and stopping -------------------------------------------------------------------------------

    @Test
    void stopWaitsForTheRunInProgress() throws Exception {
        // Regression: stop() returned at once, while the run carried on using what the caller then released
        var inRun = new CountDownLatch(1);
        var finished = new AtomicBoolean();
        var w = this.worker("Busy", () -> {
            inRun.countDown();
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                return;
            }
            finished.set(true);
        }, 1000);
        w.start();
        assertTrue(inRun.await(5, TimeUnit.SECONDS));

        assertTrue(w.stop(), "stopped within the timeout");
        assertTrue(finished.get(), "the run had finished when stop() returned");
    }

    @Test
    void stopInterruptsARunThatTakesTooLong() throws Exception {
        var inRun = new CountDownLatch(1);
        var interrupted = new CountDownLatch(1);
        var w = this.worker("Stuck", () -> {
            inRun.countDown();
            try {
                Thread.sleep(60_000);
            } catch (InterruptedException e) {
                interrupted.countDown();
            }
        }, 1000);
        w.start();
        assertTrue(inRun.await(5, TimeUnit.SECONDS));

        assertFalse(w.stop(100));

        assertTrue(interrupted.await(5, TimeUnit.SECONDS), "the run was interrupted");
        assertTrue(this.log.at(IModLog.Level.WARN).getFirst().message().contains("didn't finish within 100ms"));
    }

    @Test
    void startingTwiceIsAnError() {
        var w = this.worker("Twice", () -> {}, 1000);
        w.start();

        var e = assertThrows(IllegalStateException.class, w::start);
        assertTrue(e.getMessage().contains("already started"), e.getMessage());
    }

    @Test
    void startingAfterStopIsAnError() {
        var w = this.worker("Restart", () -> {}, 1000);
        w.start();
        w.stop();

        var e = assertThrows(IllegalStateException.class, w::start);
        assertTrue(e.getMessage().contains("create a new one"), e.getMessage());
    }

    @Test
    void stoppingAWorkerThatNeverStartedIsFine() {
        assertTrue(this.worker("Idle", () -> {}, 1000).stop());
    }

    @Test
    void noRunsAfterStop() throws Exception {
        var runs = new AtomicInteger();
        var first = new CountDownLatch(1);
        var w = this.worker("Counting", () -> {
            runs.incrementAndGet();
            first.countDown();
        }, 10);
        w.start();
        assertTrue(first.await(5, TimeUnit.SECONDS));

        w.stop();
        int atStop = runs.get();
        Thread.sleep(100);

        assertEquals(atStop, runs.get());
    }
}
