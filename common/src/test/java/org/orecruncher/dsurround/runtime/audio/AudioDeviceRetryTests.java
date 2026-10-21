package org.orecruncher.dsurround.runtime.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class AudioDeviceRetryTests {

    private long now = 1_000L;
    private boolean enabled = true;
    private final AudioDeviceRetry retry = new AudioDeviceRetry(() -> this.enabled, () -> this.now);

    @Test
    void aRunningEngineRestartsAsSoonAsItsDeviceIsLost() {
        assertFalse(this.retry.shouldRestart(false));
        assertTrue(this.retry.shouldRestart(true));
        assertTrue(this.retry.shouldRestart(true));
        assertTrue(this.retry.shouldLogRestart());
    }

    @Test
    void onceAStartFailsItRestartsOnlyEveryFewSeconds() {
        assertTrue(this.retry.startFailed());
        assertFalse(this.retry.shouldLogRestart());
        // Every tick the closed device still reads as lost
        for (int tick = 0; tick < 99; tick++) {
            this.now += 50_000_000L;
            assertFalse(this.retry.shouldRestart(true), "tick " + tick);
        }
        this.now = 1_000L + AudioDeviceRetry.RETRY_NANOS;
        assertTrue(this.retry.shouldRestart(true));
        assertFalse(this.retry.shouldRestart(true));
    }

    @Test
    void onlyTheFirstFailureIsWorthLogging() {
        assertTrue(this.retry.startFailed());
        assertFalse(this.retry.startFailed());
        assertFalse(this.retry.startFailed());
    }

    @Test
    void aSuccessfulStartPutsItBackToRestartingRightAway() {
        this.retry.startFailed();
        this.retry.started();
        assertTrue(this.retry.shouldLogRestart());
        assertTrue(this.retry.shouldRestart(true));
        // And a later loss is logged again
        assertTrue(this.retry.startFailed());
    }

    @Test
    void aDeviceThatIsntLostNeverRestarts() {
        this.retry.startFailed();
        this.now += 10 * AudioDeviceRetry.RETRY_NANOS;
        assertFalse(this.retry.shouldRestart(false));
    }

    @Test
    void turnedOffItRestartsAndLogsEveryTickAsTheGameWould() {
        this.enabled = false;
        for (int tick = 0; tick < 3; tick++) {
            this.now += 50_000_000L;
            assertTrue(this.retry.shouldRestart(true), "tick " + tick);
            assertTrue(this.retry.shouldLogRestart(), "tick " + tick);
            assertTrue(this.retry.startFailed(), "tick " + tick);
        }
    }

    @Test
    void turnedOnWhileDownItPacesFromThen() {
        this.enabled = false;
        this.retry.startFailed();
        this.retry.startFailed();
        this.enabled = true;
        assertFalse(this.retry.shouldLogRestart());
        assertFalse(this.retry.shouldRestart(true));
        this.now += AudioDeviceRetry.RETRY_NANOS;
        assertTrue(this.retry.shouldRestart(true));
    }
}
