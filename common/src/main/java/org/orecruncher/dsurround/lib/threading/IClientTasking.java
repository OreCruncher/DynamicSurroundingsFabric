package org.orecruncher.dsurround.lib.threading;

import org.jetbrains.annotations.Nullable;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * Runs code on the client (render) thread from any thread.
 */
public interface IClientTasking {

    /**
     * Runs the task on the client thread and waits for its result: directly if already on the client thread,
     * otherwise for up to a few seconds. A task still waiting to run when the time is up is cancelled.
     *
     * @throws ExecutionException   if the task threw; the cause is what it threw
     * @throws TimeoutException     if the client thread didn't run it in time
     * @throws InterruptedException if this thread was interrupted while waiting
     */
    @Nullable <T> T execute(Callable<T> task) throws ExecutionException, InterruptedException, TimeoutException;

    /**
     * Runs the task on the client thread and waits for it to finish. See {@link #execute(Callable)}.
     */
    void execute(Runnable task) throws ExecutionException, InterruptedException, TimeoutException;

    /**
     * Runs the task on the client thread without waiting: now if already on the client thread, otherwise queued
     * for it. If the task throws, the exception is logged. Use this when no result is needed; it can't block or
     * deadlock the caller.
     */
    void submit(Runnable task);
}
