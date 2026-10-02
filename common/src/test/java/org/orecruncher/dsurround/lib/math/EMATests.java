package org.orecruncher.dsurround.lib.math;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class EMATests {

    @Test
    void zeroBeforeAnySample() {
        // Regression: NaN, shown as "NaN ms" in diagnostics
        var ema = new EMA("test");

        assertEquals(0D, ema.get());
    }

    @Test
    void firstSampleIsTakenAsIs() {
        var ema = new EMA("test", 10);

        assertEquals(42D, ema.update(42D));
        assertEquals(42D, ema.get());
    }

    @Test
    void laterSamplesAreWeighted() {
        // 3 periods: factor 2 / (3 + 1) = 0.5
        var ema = new EMA("test", 3);
        ema.update(10D);

        assertEquals(15D, ema.update(20D), 1.0E-9);
        assertEquals(17.5D, ema.update(20D), 1.0E-9);
    }

    @Test
    void convergesOnAConstantValue() {
        var ema = new EMA("test", 20);
        ema.update(0D);
        for (int i = 0; i < 500; i++)
            ema.update(100D);

        assertEquals(100D, ema.get(), 1.0E-6);
    }

    @Test
    void nameDefaults() {
        assertEquals("UNNAMED", new EMA().name());
        assertEquals("scan", new EMA("scan").name());
    }

    @Test
    void timerReportsMilliseconds() {
        var timer = new TimerEMA("tick");
        timer.update(2_500_000D);

        assertEquals(2.5D, timer.getMSecs(), 1.0E-9);
        assertEquals("tick:  2.500ms", timer.toString());
    }

    @Test
    void timerBeforeAnySampleShowsZero() {
        assertEquals("tick:  0.000ms", new TimerEMA("tick").toString());
    }

    @Test
    void loggingTimerRecordsTheLastSample() throws InterruptedException {
        var timer = new LoggingTimerEMA("work");

        timer.begin();
        Thread.sleep(5);
        timer.end();

        assertTrue(timer.getLastSample() >= 4_000_000L, "nanoseconds " + timer.getLastSample());
        assertTrue(timer.getLastSampleMSecs() >= 4L, "milliseconds " + timer.getLastSampleMSecs());
        assertEquals(timer.getLastSample(), timer.get(), 1.0E-9, "the first sample is the average");
    }
}
