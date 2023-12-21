package org.orecruncher.dsurround.lib.threading;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.lib.GameUtils;

import java.util.concurrent.*;

@Environment(EnvType.CLIENT)
public final class ClientTasking implements IClientTasking {

    /**
     * Timeout settings that determine how long a call will block waiting for a result
     * of a task executing on the server thread.
     */
    private static final long TIMEOUT = 5000;
    private static final TimeUnit TIME_UNIT = TimeUnit.MILLISECONDS;

    public ClientTasking() {
    }

    /**
     * Safely executes the Callable on the server thread.  Calls will block until completed
     * or a timeout is reached.
     *
     * @param supplier Code to execute
     * @param <T>      Type of result value
     * @return Results of the execution
     */
    @Override
    @Nullable
    public <T> T execute(Callable<T> supplier) throws ExecutionException, InterruptedException, TimeoutException {
        var future = new FutureTask<>(supplier);
        GameUtils.getMC().submitAndJoin(future);
        return future.get(TIMEOUT, TIME_UNIT);
    }

    /**
     * Safely executes the Runnable on the server thread.  Calls will block until completed
     * or a timeout is reached.
     *
     * @param runnable Code to execute
     */
    @Override
    public void execute(Runnable runnable) throws ExecutionException, InterruptedException, TimeoutException {
        var future = new FutureTask<Void>(() -> {
            runnable.run();
            return null;
        });
        GameUtils.getMC().submitAndJoin(future);
        future.get(TIMEOUT, TIME_UNIT);
    }
}
