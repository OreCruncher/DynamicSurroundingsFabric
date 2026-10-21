package org.orecruncher.dsurround.runtime.audio;

import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/**
 * Paces the sound engine's restarts after its audio device is lost and can't be opened again (headphones turned off,
 * nothing else to play on). The game asks every tick whether the device is gone, and once a restart fails, the closed
 * device still reads as gone: it restarts every tick, logging a stack trace each time, until a device comes back
 * (hundreds of thousands of times overnight). While the engine is down, a lost device asks for a restart only every
 * {@link #RETRY_NANOS}, and the failure is logged once. A new system default device still restarts it right away,
 * through the game's own check for one. Turned off, the game's behavior is left as it is.
 */
public final class AudioDeviceRetry {

    static final long RETRY_NANOS = 5_000_000_000L;

    private final BooleanSupplier enabled;
    private final LongSupplier clock;
    private boolean down;
    private long lastAttempt;

    public AudioDeviceRetry(BooleanSupplier enabled, LongSupplier clock) {
        this.enabled = enabled;
        this.clock = clock;
    }

    /**
     * Whether restarts are paced: if not, the game restarts and logs as it would without this
     */
    public boolean isEnabled() {
        return this.enabled.getAsBoolean();
    }

    /**
     * Whether to restart for a lost device: always while the engine is running, else once a while
     */
    public boolean shouldRestart(boolean deviceLost) {
        if (!deviceLost)
            return false;
        if (!this.down || !this.isEnabled())
            return true;
        long now = this.clock.getAsLong();
        if (now - this.lastAttempt < RETRY_NANOS)
            return false;
        this.lastAttempt = now;
        return true;
    }

    /**
     * Whether the messages a restart logs (the device lost, sound events without sounds) are logged: not again at each
     * retry while the engine is down
     */
    public boolean shouldLogRestart() {
        return !this.down || !this.isEnabled();
    }

    /**
     * A start failed
     *
     * @return whether to log it: the first failure since the engine last ran
     */
    public boolean startFailed() {
        boolean log = this.shouldLogRestart();
        this.down = true;
        this.lastAttempt = this.clock.getAsLong();
        return log;
    }

    /**
     * A start succeeded
     */
    public void started() {
        this.down = false;
    }
}
