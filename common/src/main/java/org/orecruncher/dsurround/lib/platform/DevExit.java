// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.lib.platform;

import net.minecraft.client.Minecraft;
import org.orecruncher.dsurround.lib.logging.IModLog;

/**
 * Ends the JVM once the game has finished, in development runs only. Other mods' threads (Architectury's pools on 26.x)
 * hold it open after the game's main thread returns, until vanilla's shutdown watchdog gives up with a "Client
 * shutdown from post-main" crash report, and the Gradle task waits all that time.
 * <p>
 * Armed as the client stops, on the render thread, which is the main thread: it waits for that thread to end, after
 * the world is saved and the game closed, so it never cuts anything short.
 */
public final class DevExit {

    // After the main thread ends, how long a normal exit may take before the JVM is ended
    private static final long GRACE_MILLIS = 2000;

    private static volatile int exitCode;
    private static volatile boolean armed;

    private DevExit() {
    }

    /**
     * The exit code to end with: a bench run that failed says so (the default is 0).
     */
    public static void setExitCode(int code) {
        exitCode = code;
    }

    /**
     * Starts waiting for the calling thread, the game's main thread, to end. Once only.
     */
    public static synchronized void arm(IModLog logger) {
        if (armed)
            return;
        // Waiting on any other thread could end the game while it still runs
        if (!Minecraft.getInstance().isSameThread()) {
            logger.info("Development run: the client is stopping off the render thread (%s), so the JVM is left to exit by itself", Thread.currentThread().getName());
            return;
        }
        armed = true;
        var main = Thread.currentThread();
        var watcher = new Thread(() -> {
            try {
                main.join();
                Thread.sleep(GRACE_MILLIS);
            } catch (InterruptedException e) {
                return;
            }
            logger.info("Development run: the game has finished but the JVM is still running; ending it");
            Runtime.getRuntime().halt(exitCode);
        }, "dsurround-dev-exit");
        watcher.setDaemon(true);
        watcher.start();
    }
}
