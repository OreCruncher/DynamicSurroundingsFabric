// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.runtime.weather;

import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.runtime.audio.FixedFilter;

/**
 * A rain sound with the filter of the surface it plays on (see {@link RainSurface}), set as it starts by enhanced sound
 * processing; without that (Enhanced Sounds off) it plays as any sound does. Made as vanilla's
 * {@code ClientLevel.playLocalSound} makes its sound, when nothing delays it.
 */
public final class RainSoundInstance extends SimpleSoundInstance implements FixedFilter.Carrier {

    @Nullable
    private final FixedFilter filter;

    public RainSoundInstance(SoundEvent sound, SoundSource source, float volume, float pitch, RandomSource random,
                             double x, double y, double z, @Nullable FixedFilter filter) {
        super(sound, source, volume, pitch, random, x, y, z);
        this.filter = filter;
    }

    @Override
    public @Nullable FixedFilter fixedFilter() {
        return this.filter;
    }
}
