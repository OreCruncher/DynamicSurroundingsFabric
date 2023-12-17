package org.orecruncher.dsurround.lib.threading;

import org.jetbrains.annotations.Nullable;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public interface ITasking {

    @Nullable <T> T execute(Callable<T> supplier) throws ExecutionException, InterruptedException, TimeoutException;

    void execute(Runnable runnable) throws ExecutionException, InterruptedException, TimeoutException;
}
