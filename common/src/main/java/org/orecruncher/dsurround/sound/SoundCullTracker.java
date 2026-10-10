package org.orecruncher.dsurround.sound;

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.resources.Identifier;

/**
 * Limits how often each culled sound can play: once per interval of ticks. Thread-safe, as sounds can be played
 * from other threads by mods.
 */
final class SoundCullTracker {

    private final Object2LongOpenHashMap<Identifier> lastPlayed = new Object2LongOpenHashMap<>(32);

    /**
     * Whether a play of the sound at {@code currentTick} should be dropped: it played less than
     * {@code interval} ticks ago. A play that is allowed is recorded; the first play of a sound is always allowed.
     */
    synchronized boolean shouldCull(final Identifier id, final long currentTick, final int interval) {
        if (this.lastPlayed.containsKey(id) && currentTick - this.lastPlayed.getLong(id) < interval)
            return true;
        this.lastPlayed.put(id, currentTick);
        return false;
    }
}
