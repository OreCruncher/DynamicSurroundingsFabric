// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.runtime.weather;

import java.util.Locale;
import java.util.StringJoiner;

/**
 * The surfaces the last rain sounds played on, for the diagnostics overlay: what the rain sounds like it is falling on
 * around the player. Written from the rain tick (which may be another mod's worker thread) and read on the client
 * thread; a read racing a write only miscounts a sound, which is fine for a readout.
 */
public final class RainSurfaceTally {

    public static final int SIZE = 64;

    private final byte[] recent = new byte[SIZE];
    private volatile int count;

    public void add(RainSurface surface) {
        int n = this.count;
        this.recent[n % SIZE] = (byte) surface.ordinal();
        this.count = n + 1;
    }

    /**
     * The share of each surface over the last sounds, the commonest first: "water 41%, leaves 22%, hard 37%", or empty
     * before any
     */
    public String summary() {
        int n = Math.min(this.count, SIZE);
        if (n == 0)
            return "";
        var values = RainSurface.values();
        var counts = new int[values.length];
        for (int i = 0; i < n; i++)
            counts[this.recent[i]]++;
        var joiner = new StringJoiner(", ");
        for (int found = 0; found < values.length; found++) {
            int best = -1;
            for (int i = 0; i < counts.length; i++)
                if (counts[i] > 0 && (best < 0 || counts[i] > counts[best]))
                    best = i;
            if (best < 0)
                break;
            joiner.add(String.format(Locale.ROOT, "%s %.0f%%", values[best].name().toLowerCase(Locale.ROOT), counts[best] * 100F / n));
            counts[best] = 0;
        }
        return joiner.toString();
    }
}
