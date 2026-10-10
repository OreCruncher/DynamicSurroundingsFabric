package org.orecruncher.dsurround.runtime.audio;

import org.orecruncher.dsurround.eventing.*;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Counts changes that can alter how sound travels: block updates, chunk loads, library reloads (block reflectivity
 * and occlusion), configuration changes, and leaving a world. Ray traced sound results are reused while the count
 * is unchanged (see {@link TraceCache}).
 * <p>
 * Any change anywhere counts, so busy worlds (redstone, flowing water, crops) reuse results less often. That only
 * costs the work the cache would have saved.
 */
public final class WorldChangeTracker {

    private static final AtomicLong GENERATION = new AtomicLong();
    private static boolean registered;

    private WorldChangeTracker() {
    }

    /**
     * Hooks the events that count as changes. Calling it again does nothing.
     */
    public static synchronized void register() {
        if (registered)
            return;
        registered = true;
        IBlockUpdates.EVENT.register(positions -> changed());
        IChunkLoad.EVENT.register((level, chunkPos) -> changed());
        IReloadEvent.EVENT.register((resources, scope) -> changed());
        IConfigChangedEvent.EVENT.register(config -> changed());
        IClientDisconnect.EVENT.register(client -> changed());
    }

    /**
     * The current count. Read it before tracing, so a change during tracing is picked up next time.
     */
    public static long generation() {
        return GENERATION.get();
    }

    public static void changed() {
        GENERATION.incrementAndGet();
    }
}
