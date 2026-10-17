package org.orecruncher.dsurround.lib.function;

import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.threading.RecordingLog;

import static org.junit.jupiter.api.Assertions.*;

public class GuardTests {

    @Test
    void getReturnsTheBodysValue() {
        var log = new RecordingLog();
        var guard = Guard.named("Test", log);
        assertEquals(5, guard.get(() -> 5, 0));
        assertTrue(log.entries.isEmpty());
    }

    @Test
    void getFallsBackAndLogsWhenTheBodyThrows() {
        var log = new RecordingLog();
        var guard = Guard.named("Sky tint", log);
        assertEquals(0, guard.get(() -> {
            throw new IllegalStateException("boom");
        }, 0));
        var errors = log.at(IModLog.Level.ERROR);
        assertEquals(1, errors.size());
        assertTrue(errors.getFirst().message().contains("Sky tint"));
        assertInstanceOf(IllegalStateException.class, errors.getFirst().throwable());
    }

    @Test
    void runSaysWhetherTheBodyFinished() {
        var guard = Guard.named("Test", new RecordingLog());
        assertTrue(guard.run(() -> {
        }));
        assertFalse(guard.run(() -> {
            throw new IllegalStateException();
        }));
    }

    @Test
    void keepsRunningAfterAFailure() {
        var guard = Guard.named("Test", new RecordingLog());
        guard.get(() -> {
            throw new IllegalStateException();
        }, 0);
        assertEquals(7, guard.get(() -> 7, 0));
    }

    @Test
    void repeatedFailuresAreLoggedOnlyAFewTimes() {
        var log = new RecordingLog();
        var guard = Guard.named("Test", log);
        for (int i = 0; i < 100; i++)
            guard.failed(new IllegalStateException());
        // The first few, and a notice that the rest are dropped
        assertTrue(log.entries.size() < 15, "logged " + log.entries.size());
    }

    @Test
    void anUnlimitedGuardLogsEveryFailure() {
        var log = new RecordingLog();
        var guard = Guard.named("Test", log, Guard.UNLIMITED);
        for (int i = 0; i < 100; i++)
            guard.failed(new IllegalStateException());
        assertEquals(100, log.at(IModLog.Level.ERROR).size());
    }

    @Test
    void aResetLogsFailuresAgain() {
        var log = new RecordingLog();
        var guard = Guard.named("Test", log, 1);
        guard.failed(new IllegalStateException());
        guard.failed(new IllegalStateException());
        assertEquals(1, failures(log));
        guard.reset();
        guard.failed(new IllegalStateException());
        assertEquals(2, failures(log));
    }

    // The failures logged, leaving out the notice that further ones won't be
    private static long failures(RecordingLog log) {
        return log.entries.stream().filter(e -> e.message().startsWith("Test failed")).count();
    }

    @Test
    void whatFailedIsNamed() {
        var log = new RecordingLog();
        var guard = Guard.named("Applying biome rules", log);
        assertFalse(guard.run(() -> {
            throw new IllegalStateException();
        }, "minecraft:plains"));
        var message = log.at(IModLog.Level.ERROR).getFirst().message();
        assertTrue(message.contains("Applying biome rules") && message.contains("minecraft:plains"), message);
    }

    @Test
    void errorsTheJvmCantRecoverFromArePassedOn() {
        var guard = Guard.named("Test", new RecordingLog());
        assertThrows(OutOfMemoryError.class, () -> guard.run(() -> {
            throw new OutOfMemoryError();
        }));
    }
}
