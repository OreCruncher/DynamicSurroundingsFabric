package org.orecruncher.dsurround.lib.threading;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.logging.LogThrottle;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

public class TasksTests {

    @AfterEach
    void clearInterrupt() {
        // Don't leave the test thread interrupted for the next test
        Thread.interrupted();
    }

    @Test
    void waitsForEveryTask() {
        var log = new RecordingLog();
        var tasks = List.<Future<?>>of(CompletableFuture.completedFuture(1), CompletableFuture.completedFuture(2));
        assertTrue(Tasks.awaitAll(tasks, LogThrottle.firstN(log, "test", null, 10), "A task"));
        assertTrue(log.entries.isEmpty());
    }

    @Test
    void aFailedTaskIsLoggedAndTheRestStillWaitedFor() {
        var log = new RecordingLog();
        var cause = new NoSuchMethodError("renamed");
        var later = new CompletableFuture<Integer>();
        var waitedForLater = new boolean[1];
        var tasks = List.<Future<?>>of(
                CompletableFuture.failedFuture(cause),
                later.whenComplete((v, t) -> waitedForLater[0] = true));
        later.complete(3);

        assertTrue(Tasks.awaitAll(tasks, LogThrottle.firstN(log, "test", null, 10), "A sound task"));
        var errors = log.at(IModLog.Level.ERROR);
        assertEquals(1, errors.size());
        assertSame(cause, errors.getFirst().throwable(), "logged the wrapper rather than what the task threw");
        assertTrue(errors.getFirst().message().contains("A sound task failed"));
        assertTrue(waitedForLater[0]);
    }

    @Test
    void stopsWaitingWhenInterruptedAndKeepsTheInterrupt() {
        // A task that never finishes: without stopping on the interrupt this would wait forever
        var never = new CompletableFuture<Integer>();
        Thread.currentThread().interrupt();

        assertFalse(Tasks.awaitAll(List.of(never), LogThrottle.firstN(new RecordingLog(), "test", null, 10), "A task"));
        assertTrue(Thread.currentThread().isInterrupted(), "the interrupt was swallowed");
    }

    @Test
    void failuresAreLoggedOnlySoOften() {
        var log = new RecordingLog();
        var errors = LogThrottle.<Object>firstN(log, "test", null, 3);
        for (int i = 0; i < 10; i++)
            Tasks.awaitAll(List.of(CompletableFuture.failedFuture(new IllegalStateException())), errors, "A task");
        assertTrue(log.at(IModLog.Level.ERROR).size() <= 3 + 1, "every failure was logged");
    }
}
