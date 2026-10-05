package org.orecruncher.dsurround.effects;

import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.threading.RecordingLog;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

public class RenderFailSafeTests {

    private static final Runnable NOTHING = () -> {
    };

    @Test
    void runsTheBody() {
        var log = new RecordingLog();
        var safe = new RenderFailSafe("test", log);
        var ran = new int[1];
        assertTrue(safe.run(() -> ran[0]++, NOTHING));
        assertTrue(safe.run(() -> ran[0]++, NOTHING));
        assertEquals(2, ran[0]);
        assertFalse(safe.isFailed());
        assertTrue(log.entries.isEmpty());
    }

    @Test
    void aFailureIsCaughtLoggedAndTidiedUp() {
        var log = new RecordingLog();
        var safe = new RenderFailSafe("the aurora", log);
        var events = new ArrayList<String>();
        var error = new IllegalStateException("boom");

        var result = safe.run(() -> {
            try {
                events.add("draw");
                throw error;
            } finally {
                // Drawing state is put back in the body's own finally
                events.add("restore state");
            }
        }, () -> events.add("tidy up"));

        assertFalse(result);
        assertTrue(safe.isFailed());
        assertEquals(java.util.List.of("draw", "restore state", "tidy up"), events);
        var errors = log.at(IModLog.Level.ERROR);
        assertEquals(1, errors.size());
        assertSame(error, errors.getFirst().throwable());
        assertTrue(errors.getFirst().message().contains("the aurora"));
    }

    @Test
    void staysOffAfterFailingAndLogsOnlyOnce() {
        var log = new RecordingLog();
        var safe = new RenderFailSafe("test", log);
        safe.run(() -> {
            throw new RuntimeException("first");
        }, NOTHING);

        var ran = new int[1];
        for (int frame = 0; frame < 100; frame++)
            assertFalse(safe.run(() -> ran[0]++, NOTHING));
        assertEquals(0, ran[0], "drew again after failing");
        assertEquals(1, log.at(IModLog.Level.ERROR).size());
    }

    @Test
    void resetTurnsItBackOn() {
        var log = new RecordingLog();
        var safe = new RenderFailSafe("test", log);
        safe.run(() -> {
            throw new RuntimeException();
        }, NOTHING);
        safe.reset();
        assertFalse(safe.isFailed());
        var ran = new int[1];
        assertTrue(safe.run(() -> ran[0]++, NOTHING));
        assertEquals(1, ran[0]);
    }

    @Test
    void unrecoverableErrorsArePassedOn() {
        var log = new RecordingLog();
        var safe = new RenderFailSafe("test", log);
        assertThrows(OutOfMemoryError.class, () -> safe.run(() -> {
            throw new OutOfMemoryError();
        }, NOTHING));
        // Not a failure of the effect's own: it isn't turned off
        assertFalse(safe.isFailed());
        assertTrue(log.entries.isEmpty());
    }

    @Test
    void aFailingTidyUpIsLoggedNotThrown() {
        var log = new RecordingLog();
        var safe = new RenderFailSafe("test", log);
        assertDoesNotThrow(() -> safe.run(() -> {
            throw new RuntimeException("draw");
        }, () -> {
            throw new RuntimeException("tidy");
        }));
        assertTrue(safe.isFailed());
        assertEquals(2, log.at(IModLog.Level.ERROR).size());
    }

    @Test
    void errorsAreAlsoCaught() {
        // A linkage problem from a changed game class is an Error, not an Exception, but not unrecoverable
        var safe = new RenderFailSafe("test", new RecordingLog());
        assertFalse(safe.run(() -> {
            throw new NoSuchMethodError("renamed");
        }, NOTHING));
        assertTrue(safe.isFailed());
    }
}
