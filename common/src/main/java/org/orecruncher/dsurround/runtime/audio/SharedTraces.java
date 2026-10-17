package org.orecruncher.dsurround.runtime.audio;

import java.lang.ref.WeakReference;

/**
 * Recent ray traced results, shared between sounds. Sounds that start together, like the clatter of a collision,
 * mostly start in the same few blocks; a sound in a block traced moments ago takes that result instead of tracing
 * again. That is as exact as {@link TraceCache}, which already ignores movement within a block: an entry matches on
 * the same conditions (world, sound block, eye block, world change generation), plus whether occlusion was traced.
 * <p>
 * The generation only goes up, so entries from an older one are dropped all at once. The world is held weakly: a
 * table kept by a pool thread must not keep the last world loaded after leaving it.
 * <p>
 * One per thread, so not thread-safe.
 */
final class SharedTraces {

    private static final class Entry {
        long soundBlock;
        long eyeBlock;
        boolean occlusion;
        boolean valid;
        final TraceResult result;

        Entry(int channels, int bounces) {
            this.result = new TraceResult(channels, bounces);
        }
    }

    private final Entry[] entries;
    // The oldest entry, replaced by the next store
    private int next;
    private WeakReference<Object> world = new WeakReference<>(null);
    private long generation;

    SharedTraces(int capacity, int channels, int bounces) {
        this.entries = new Entry[capacity];
        for (int i = 0; i < capacity; i++)
            this.entries[i] = new Entry(channels, bounces);
    }

    /**
     * Copies a result traced under these conditions into {@code into}, if there is one.
     *
     * @return whether one was found
     */
    boolean find(Object world, long soundBlock, long eyeBlock, long generation, boolean occlusion, TraceResult into) {
        if (this.world.get() != world || this.generation != generation)
            return false;
        for (var entry : this.entries) {
            if (entry.valid && entry.soundBlock == soundBlock && entry.eyeBlock == eyeBlock && entry.occlusion == occlusion) {
                into.copyFrom(entry.result);
                return true;
            }
        }
        return false;
    }

    /**
     * Keeps a result for other sounds. {@code generation} is the one read before tracing; a result from before a
     * change already seen by this table is not kept.
     */
    void store(Object world, long soundBlock, long eyeBlock, long generation, boolean occlusion, TraceResult from) {
        if (this.world.get() != world || generation > this.generation) {
            this.clear();
            this.world = new WeakReference<>(world);
            this.generation = generation;
        } else if (generation < this.generation) {
            return;
        }

        var entry = this.entries[this.next];
        this.next = (this.next + 1) % this.entries.length;
        entry.soundBlock = soundBlock;
        entry.eyeBlock = eyeBlock;
        entry.occlusion = occlusion;
        entry.result.copyFrom(from);
        entry.valid = true;
    }

    private void clear() {
        for (var entry : this.entries)
            entry.valid = false;
        this.next = 0;
    }
}
