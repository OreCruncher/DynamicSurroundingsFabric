package org.orecruncher.dsurround.lib.function;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for a boolean asked for again at most once per interval.
 */
public class ThrottledBooleanSupplierTests {

    private static final long INTERVAL = 50;

    @Test
    void askedOnceThenKeptUntilTheIntervalIsUp() {
        var now = new AtomicLong(1_000);
        var answer = new AtomicBoolean(true);
        var asked = new AtomicInteger();
        var supplier = new ThrottledBooleanSupplier(() -> {
            asked.incrementAndGet();
            return answer.get();
        }, INTERVAL, now::get);

        assertTrue(supplier.getAsBoolean());
        assertEquals(1, asked.get());

        // The answer changes, but within the interval the last one is given without asking
        answer.set(false);
        now.addAndGet(INTERVAL - 1);
        assertTrue(supplier.getAsBoolean());
        assertEquals(1, asked.get());

        // Once the interval is up it is asked again
        now.addAndGet(1);
        assertFalse(supplier.getAsBoolean());
        assertEquals(2, asked.get());
    }

    @Test
    void theFirstCallAlwaysAsks() {
        // Even at time zero, before any interval could have passed
        var asked = new AtomicInteger();
        var supplier = new ThrottledBooleanSupplier(() -> asked.incrementAndGet() > 0, INTERVAL, () -> 0L);
        assertTrue(supplier.getAsBoolean());
        assertEquals(1, asked.get());
    }
}
