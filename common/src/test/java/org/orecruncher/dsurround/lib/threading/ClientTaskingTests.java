package org.orecruncher.dsurround.lib.threading;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.orecruncher.dsurround.lib.logging.IModLog;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ClientTasking, with a single-thread executor standing in for the client thread.
 */
@Timeout(value = 10, unit = TimeUnit.SECONDS)
public class ClientTaskingTests {

    private static final long TIMEOUT_MS = 200;

    private ExecutorService client;
    private volatile Thread clientThread;
    private RecordingLog log;
    private ClientTasking tasking;

    @BeforeEach
    void setUp() throws Exception {
        this.client = Executors.newSingleThreadExecutor(r -> new Thread(r, "test-client"));
        this.clientThread = this.client.submit(Thread::currentThread).get();
        this.log = new RecordingLog();
        this.tasking = new ClientTasking(this.client, () -> Thread.currentThread() == this.clientThread, TIMEOUT_MS, this.log);
    }

    @AfterEach
    void tearDown() {
        this.client.shutdownNow();
    }

    /**
     * Occupies the client thread until the returned latch is released.
     */
    private CountDownLatch blockClientThread() throws InterruptedException {
        var release = new CountDownLatch(1);
        var started = new CountDownLatch(1);
        this.client.execute(() -> {
            started.countDown();
            try {
                release.await();
            } catch (InterruptedException ignored) {
            }
        });
        started.await();
        return release;
    }

    /**
     * Waits until everything queued on the client thread so far has run.
     */
    private void drainClientThread() throws Exception {
        this.client.submit(() -> {
        }).get();
    }

    // ---- execute ---------------------------------------------------------------------------------------------

    @Test
    void runsOnTheClientThreadAndReturnsTheResult() throws Exception {
        var ranOn = new AtomicReference<Thread>();

        var result = this.tasking.execute(() -> {
            ranOn.set(Thread.currentThread());
            return 42;
        });

        assertEquals(42, result);
        assertSame(this.clientThread, ranOn.get());
    }

    @Test
    void runnableVersionWaitsForTheTask() throws Exception {
        var ran = new AtomicBoolean();

        this.tasking.execute(() -> ran.set(true));

        assertTrue(ran.get());
    }

    @Test
    void runsDirectlyWhenAlreadyOnTheClientThread() throws Exception {
        // Queuing it and waiting would deadlock: the client thread would be waiting on itself
        var result = this.client.submit(() -> this.tasking.execute(() -> Thread.currentThread() == this.clientThread)).get();

        assertTrue(result);
    }

    @Test
    void timesOutWhenTheClientThreadIsBusy() throws Exception {
        // Regression: Minecraft's executeBlocking waited with no limit, so the timeout never applied
        var release = this.blockClientThread();
        var ran = new AtomicBoolean();
        try {
            long start = System.nanoTime();
            assertThrows(TimeoutException.class, () -> this.tasking.execute(() -> ran.set(true)));
            long waitedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            // At least the timeout. No tight upper bound: a busy machine can delay waking this thread (once seen at
            // 2.5s), and the class's @Timeout already catches a real hang
            assertTrue(waitedMs >= TIMEOUT_MS - 20, "waited " + waitedMs + "ms");
        } finally {
            release.countDown();
        }

        // Cancelled when the caller gave up, so it doesn't run late
        this.drainClientThread();
        assertFalse(ran.get());
    }

    @Test
    void exceptionIsTheCauseOfExecutionException() {
        var failure = new IllegalStateException("boom");

        var e = assertThrows(ExecutionException.class, () -> this.tasking.execute(() -> {
            throw failure;
        }));

        assertSame(failure, e.getCause());
    }

    @Test
    void exceptionOnTheClientThreadIsWrappedTheSameWay() throws Exception {
        var failure = new IllegalStateException("boom");

        var cause = this.client.submit(() -> {
            try {
                this.tasking.execute(() -> {
                    throw failure;
                });
                return null;
            } catch (ExecutionException e) {
                return e.getCause();
            }
        }).get();

        assertSame(failure, cause);
    }

    // ---- submit ----------------------------------------------------------------------------------------------

    @Test
    void submitDoesNotWait() throws Exception {
        var release = this.blockClientThread();
        var ran = new CountDownLatch(1);
        try {
            // The client thread stays blocked until the latch is released below, so if submit waited for the task
            // it would never return (and the class's @Timeout would fail the test). No clock needed.
            this.tasking.submit(ran::countDown);

            assertEquals(1, ran.getCount(), "returned before the task ran: the client thread is busy");
        } finally {
            release.countDown();
        }

        assertTrue(ran.await(5, TimeUnit.SECONDS), "runs once the client thread is free");
    }

    @Test
    void submitRunsOnTheClientThread() throws Exception {
        var ranOn = new AtomicReference<Thread>();

        this.tasking.submit(() -> ranOn.set(Thread.currentThread()));
        this.drainClientThread();

        assertSame(this.clientThread, ranOn.get());
    }

    @Test
    void submitOnTheClientThreadRunsImmediately() throws Exception {
        var ranBeforeReturning = this.client.submit(() -> {
            var ran = new AtomicBoolean();
            this.tasking.submit(() -> ran.set(true));
            return ran.get();
        }).get();

        assertTrue(ranBeforeReturning);
    }

    @Test
    void submitLogsAnExceptionInsteadOfPassingItOn() throws Exception {
        var failure = new IllegalStateException("boom");
        var after = new AtomicBoolean();

        this.tasking.submit(() -> {
            throw failure;
        });
        this.tasking.submit(() -> after.set(true));
        this.drainClientThread();

        var errors = this.log.at(IModLog.Level.ERROR);
        assertEquals(1, errors.size(), errors.toString());
        assertSame(failure, errors.getFirst().throwable());
        assertTrue(after.get(), "the client thread kept running tasks");
    }
}
