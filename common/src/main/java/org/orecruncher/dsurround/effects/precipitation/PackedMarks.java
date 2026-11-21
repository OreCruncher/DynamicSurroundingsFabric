// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.precipitation;

import net.minecraft.util.Mth;

import java.util.concurrent.atomic.AtomicLong;

/**
 * What the drops left where they landed ({@link SnowFlecks}, {@link WindowStreaks}, {@link DampSpots}), packed for the
 * GPU to shape (see {@link GpuMarks}): one record of floats each, three texels of four floats, positions relative to an
 * anchor near the camera as it was packed. What changes between ticks (a streak running down, a fleck melting) is packed
 * as it was and is, or as its age, for the shader to work out between them, as the CPU's build does. Packed once a tick,
 * by the first build after the marks changed ({@link #invalidate}, which the client tick calls once it has changed
 * them), on whichever thread builds first, hence the lock.
 */
final class PackedMarks {

    // Three texels of four floats
    static final int STRIDE = 12;

    // Each pack's number, from one count across all of them, so the GPU's copy is replaced when it changes
    private static final AtomicLong PACKS = new AtomicLong();

    /**
     * Marks that can be packed: each writes its own record (see the shader, precipitation_marks.vsh, for each layout)
     */
    interface Source {
        int count();

        /**
         * Writes mark {@code i}'s record into {@code r} at {@code at}, positions relative to the anchor
         */
        void pack(int i, float[] r, int at, double anchorX, double anchorY, double anchorZ);
    }

    float[] data = new float[0];
    int count;
    double anchorX, anchorY, anchorZ;
    long version;
    private boolean stale = true;

    /**
     * Says the marks changed: the next build packs them again
     */
    synchronized void invalidate() {
        this.stale = true;
    }

    /**
     * Packs the marks, if they changed since they were last, around a camera at the given position
     */
    synchronized void packIfStale(Source marks, double cameraX, double cameraY, double cameraZ) {
        if (!this.stale)
            return;
        int count = marks.count();
        int needed = count * STRIDE;
        if (this.data.length < needed)
            this.data = new float[Math.max(needed, this.data.length + this.data.length / 2)];
        this.anchorX = Mth.floor(cameraX);
        this.anchorY = Mth.floor(cameraY);
        this.anchorZ = Mth.floor(cameraZ);
        for (int i = 0; i < count; i++)
            marks.pack(i, this.data, i * STRIDE, this.anchorX, this.anchorY, this.anchorZ);
        this.count = count;
        this.version = PACKS.incrementAndGet();
        this.stale = false;
    }
}
