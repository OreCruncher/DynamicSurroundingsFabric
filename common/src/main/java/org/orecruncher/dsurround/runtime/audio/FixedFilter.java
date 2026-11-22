// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.runtime.audio;

import org.jetbrains.annotations.Nullable;

/**
 * A filter set on a sound once, as it starts, and left as it is: the tone of what the sound is heard through or comes
 * from (rain on metal, soft ground), not the acoustics of where it plays, which enhanced sound processing works out as
 * a sound goes on. OpenAL's filters only take away: a low-pass cuts the treble (above about 5 kHz in OpenAL Soft), a
 * high-pass the bass (below about 250 Hz), a band-pass both. Gains are 0 to 1, 1 changing nothing.
 *
 * @param type   which filter
 * @param gainLF what is left of the bass, for a high-pass or band-pass
 * @param gainHF what is left of the treble, for a low-pass or band-pass
 */
public record FixedFilter(Type type, float gainLF, float gainHF) {

    public enum Type {
        LOW_PASS,
        HIGH_PASS,
        BAND_PASS
    }

    public FixedFilter {
        if (!(gainLF >= 0F && gainLF <= 1F) || !(gainHF >= 0F && gainHF <= 1F))
            throw new IllegalArgumentException("filter gains are 0 to 1: " + gainLF + ", " + gainHF);
    }

    /**
     * Cuts the treble to this share
     */
    public static FixedFilter lowPass(float gainHF) {
        return new FixedFilter(Type.LOW_PASS, 1F, gainHF);
    }

    /**
     * Cuts the bass to this share
     */
    public static FixedFilter highPass(float gainLF) {
        return new FixedFilter(Type.HIGH_PASS, gainLF, 1F);
    }

    /**
     * Cuts the bass and the treble to these shares
     */
    public static FixedFilter bandPass(float gainLF, float gainHF) {
        return new FixedFilter(Type.BAND_PASS, gainLF, gainHF);
    }

    /**
     * A sound that carries a filter of its own, set as it starts (see {@link EnhancedSoundProcessor}). It gets nothing
     * else from enhanced sound processing: no reverb, no occlusion. Null for none, and the sound plays as it is.
     */
    public interface Carrier {
        @Nullable
        FixedFilter fixedFilter();
    }
}
