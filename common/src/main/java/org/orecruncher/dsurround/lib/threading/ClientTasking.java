package org.orecruncher.dsurround.lib.threading;

import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.logging.IModLog;

import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/**
 * Runs code on the Minecraft client thread.
 * <p>
 * Not built on Minecraft's {@code executeBlocking}: that waits with no time limit, so a caller holding something
 * the client thread needs would wait forever. Here a blocking call gives up after {@link #TIMEOUT_MS}.
 */
public final class ClientTasking implements IClientTasking {

    /**
     * How long a blocking call waits for the client thread to run its task.
     */
    static final long TIMEOUT_MS = 5000;

    private final Executor clientExecutor;
    private final BooleanSupplier onClientThread;
    private final long timeoutMs;
    private final IModLog logger;

    public ClientTasking() {
        // Minecraft is looked up on each use: this may be created before the client exists
        this(task -> GameUtils.getMC().execute(task), () -> GameUtils.getMC().isSameThread(), TIMEOUT_MS, Library.LOGGER);
    }

    /**
     * For tests: any executor can stand in for the client thread.
     */
    ClientTasking(Executor clientExecutor, BooleanSupplier onClientThread, long timeoutMs, IModLog logger) {
        this.clientExecutor = clientExecutor;
        this.onClientThread = onClientThread;
        this.timeoutMs = timeoutMs;
        this.logger = logger;
    }

    @Override
    @Nullable
    public <T> T execute(Callable<T> task) throws ExecutionException, InterruptedException, TimeoutException {
        if (this.onClientThread.getAsBoolean()) {
            // Queuing it and waiting would deadlock: this thread is the one that would have to run it
            try {
                return task.call();
            } catch (Exception e) {
                throw new ExecutionException(e);
            }
        }

        var future = new FutureTask<>(task);
        this.clientExecutor.execute(future);
        try {
            return future.get(this.timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException | InterruptedException e) {
            // The caller is giving up, so don't run it later if it hasn't started
            future.cancel(false);
            throw e;
        }
    }

    @Override
    public void execute(Runnable task) throws ExecutionException, InterruptedException, TimeoutException {
        this.execute(Executors.callable(task));
    }

    @Override
    public void submit(Runnable task) {
        Runnable guarded = () -> {
            try {
                task.run();
            } catch (Exception e) {
                // Logged here: an exception escaping into Minecraft's task queue can be treated as fatal
                this.logger.error(e, "Error running task on the client thread");
            }
        };
        if (this.onClientThread.getAsBoolean())
            guarded.run();
        else
            this.clientExecutor.execute(guarded);
    }
}
