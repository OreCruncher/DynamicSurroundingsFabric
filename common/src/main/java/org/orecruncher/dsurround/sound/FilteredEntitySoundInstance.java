// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.sound;

import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.runtime.audio.FixedFilter;

/**
 * A sound that follows an entity with a fixed filter of its own (a wet step's slap on hard ground), set once as it
 * starts with enhanced sound processing, and none of that processing's reverb or occlusion; without it (Enhanced Sounds
 * off) it plays as any sound does.
 */
public final class FilteredEntitySoundInstance extends EntityBoundSoundInstance implements FixedFilter.Carrier {

    private final FixedFilter filter;

    public FilteredEntitySoundInstance(SoundEvent sound, SoundSource source, float volume, float pitch, Entity entity,
                                       long seed, FixedFilter filter) {
        super(sound, source, volume, pitch, entity, seed);
        this.filter = filter;
    }

    @Override
    public @Nullable FixedFilter fixedFilter() {
        return this.filter;
    }
}
