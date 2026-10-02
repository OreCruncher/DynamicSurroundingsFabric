package org.orecruncher.dsurround.lib.logging;

import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class LogThrottleTests {

    /**
     * Records what was logged, already formatted, so tests can check both counts and wording.
     */
    private static final class RecordingLog implements IModLog {
        final List<String> errors = Collections.synchronizedList(new ArrayList<>());
        final List<String> warnings = Collections.synchronizedList(new ArrayList<>());

        @Override
        public boolean isDebugging() {
            return false;
        }

        @Override
        public boolean isTracing(int mask) {
            return false;
        }

        @Override
        public void error(Throwable e, String msg, @Nullable Object... parms) {
            this.errors.add(String.format(msg, parms));
        }

        @Override
        public void warn(String msg, @Nullable Object... parms) {
            this.warnings.add(String.format(msg, parms));
        }
    }

    private static final Exception FAILURE = new RuntimeException("boom");

    // ---- Once per key ----------------------------------------------------------------------------------------

    @Test
    void oncePerKeyLogsTheFirstMessageForEachKey() {
        var log = new RecordingLog();
        LogThrottle<String> throttle = LogThrottle.oncePerKey(log, "script errors", "the next reload");

        for (int i = 0; i < 5; i++) {
            throttle.error("a", FAILURE, "failed: %s", "a");
            throttle.error("b", FAILURE, "failed: %s", "b");
        }

        assertEquals(2, log.errors.size());
        assertTrue(log.errors.get(0).startsWith("failed: a"));
        assertTrue(log.errors.get(1).startsWith("failed: b"));
        assertEquals(8, throttle.suppressedCount());
        assertTrue(log.warnings.isEmpty(), "no overall limit, so no overall notice");
    }

    @Test
    void lastMessageForAKeySaysFurtherOnesAreNotLogged() {
        var log = new RecordingLog();
        LogThrottle<String> throttle = LogThrottle.oncePerKey(log, "script errors", "the next reload");

        throttle.error("a", FAILURE, "failed");

        assertEquals("failed (further occurrences are not logged until the next reload)", log.errors.get(0));
    }

    @Test
    void withoutAResetConditionTheNoteSaysSo() {
        var log = new RecordingLog();
        LogThrottle<String> throttle = LogThrottle.oncePerKey(log, "script errors", null);

        throttle.warn("a", "odd");

        assertEquals("odd (further occurrences are not logged)", log.warnings.get(0));
    }

    @Test
    void resetLetsEveryKeyLogAgain() {
        var log = new RecordingLog();
        LogThrottle<String> throttle = LogThrottle.oncePerKey(log, "script errors", "the next reload");

        throttle.error("a", FAILURE, "failed");
        throttle.error("a", FAILURE, "failed");
        throttle.reset();
        throttle.error("a", FAILURE, "failed");

        assertEquals(2, log.errors.size());
        assertEquals(0, throttle.suppressedCount());
    }

    @Test
    void keysCompareWithEquals() {
        var log = new RecordingLog();
        LogThrottle<String> throttle = LogThrottle.oncePerKey(log, "script errors", null);

        throttle.error(new String("same"), FAILURE, "failed");
        throttle.error(new String("same"), FAILURE, "failed");

        assertEquals(1, log.errors.size());
    }

    // ---- First N ---------------------------------------------------------------------------------------------

    @Test
    void firstNLogsNThenOneNotice() {
        var log = new RecordingLog();
        var throttle = LogThrottle.firstN(log, "block errors", null, 3);

        for (int i = 0; i < 10; i++)
            throttle.error(FAILURE, "error %d", i);

        assertEquals(List.of("error 0", "error 1", "error 2"), log.errors);
        assertEquals(List.of("3 block errors logged; further ones are not logged"), log.warnings);
        assertEquals(7, throttle.suppressedCount());
    }

    @Test
    void firstNNoticeNamesTheResetCondition() {
        var log = new RecordingLog();
        var throttle = LogThrottle.firstN(log, "block errors", "the next reload", 1);

        throttle.error(FAILURE, "error");

        assertEquals(List.of("1 block errors logged; further ones are not logged until the next reload"), log.warnings);
    }

    @Test
    void bothLimitsTogether() {
        var log = new RecordingLog();
        var throttle = new LogThrottle<String>(log, "errors", null, 2, 3);

        throttle.error("a", FAILURE, "a1");
        throttle.error("a", FAILURE, "a2");
        throttle.error("a", FAILURE, "a3");  // over the per-key limit
        throttle.error("b", FAILURE, "b1");  // the third message overall: the last allowed
        throttle.error("c", FAILURE, "c1");  // over the overall limit

        assertEquals(3, log.errors.size());
        assertTrue(log.errors.get(1).startsWith("a2 (further"), "a2 is the last allowed for key a");
        assertEquals("b1", log.errors.get(2));
        assertEquals(1, log.warnings.size());
        assertEquals(2, throttle.suppressedCount());
    }

    @Test
    void limitsMustBePositive() {
        var log = new RecordingLog();
        assertThrows(IllegalArgumentException.class, () -> new LogThrottle<String>(log, "x", null, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new LogThrottle<String>(log, "x", null, 1, 0));
    }

    // ---- Lazy arguments --------------------------------------------------------------------------------------

    @Test
    void causeSupplierOnlyRunsWhenLogged() {
        var log = new RecordingLog();
        LogThrottle<String> throttle = LogThrottle.oncePerKey(log, "script errors", null);
        var calls = new AtomicInteger();

        for (int i = 0; i < 5; i++)
            throttle.error("a", () -> {
                calls.incrementAndGet();
                return FAILURE;
            }, "failed");

        assertEquals(1, calls.get());
        assertEquals(1, log.errors.size());
    }

    @Test
    void messageSupplierOnlyRunsWhenLoggedAndIsNotAFormat() {
        var log = new RecordingLog();
        LogThrottle<String> throttle = LogThrottle.oncePerKey(log, "script errors", null);
        var calls = new AtomicInteger();

        for (int i = 0; i < 5; i++)
            throttle.error("a", FAILURE, () -> {
                calls.incrementAndGet();
                return "100% broken";  // a '%' that would break String.format if used as a format
            });

        assertEquals(1, calls.get());
        assertEquals("100% broken (further occurrences are not logged)", log.errors.get(0));
    }

    // ---- Threads ---------------------------------------------------------------------------------------------

    @Test
    void concurrentMessagesForOneKeyLogOnce() throws Exception {
        var log = new RecordingLog();
        LogThrottle<String> throttle = LogThrottle.oncePerKey(log, "script errors", null);
        int threads = 8;
        int perThread = 1_000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        var start = new CountDownLatch(1);

        try {
            for (int t = 0; t < threads; t++) {
                pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < perThread; i++)
                        throttle.error("same", FAILURE, "failed");
                    return null;
                });
            }
            start.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }

        assertEquals(1, log.errors.size());
        assertEquals((long) threads * perThread - 1, throttle.suppressedCount());
    }

    @Test
    void concurrentFirstNLogsExactlyNAndOneNotice() throws Exception {
        var log = new RecordingLog();
        var throttle = LogThrottle.firstN(log, "block errors", null, 10);
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        var start = new CountDownLatch(1);

        try {
            for (int t = 0; t < threads; t++) {
                pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < 500; i++)
                        throttle.error(FAILURE, "error");
                    return null;
                });
            }
            start.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }

        assertEquals(10, log.errors.size());
        assertEquals(1, log.warnings.size());
    }
}
