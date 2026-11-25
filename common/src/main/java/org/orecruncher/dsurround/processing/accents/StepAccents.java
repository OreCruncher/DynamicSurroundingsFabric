// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.processing.accents;

import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.runtime.audio.FixedFilter;
import org.orecruncher.dsurround.sound.ISoundFactory;

import java.util.Arrays;

/**
 * The accents gathered for a footstep: each a sound, how loud and how high to play it, as shares of the sound's own,
 * and a fixed filter if it has one. Reused from step to step.
 */
public final class StepAccents {

    private final ObjectArray<ISoundFactory> factories = new ObjectArray<>(4);
    private final ObjectArray<FixedFilter> filters = new ObjectArray<>(4);
    private float[] volumeScales = new float[4];
    private float[] pitchScales = new float[4];

    public interface IAccentConsumer {
        void accept(ISoundFactory factory, float volumeScale, float pitchScale, @Nullable FixedFilter filter);
    }

    /**
     * Adds an accent at the sound's own volume
     */
    public void add(ISoundFactory factory) {
        this.add(factory, 1F);
    }

    public void add(ISoundFactory factory, float volumeScale) {
        this.add(factory, volumeScale, 1F, null);
    }

    public void add(ISoundFactory factory, float volumeScale, float pitchScale, @Nullable FixedFilter filter) {
        int index = this.factories.size();
        if (index == this.volumeScales.length) {
            this.volumeScales = Arrays.copyOf(this.volumeScales, index * 2);
            this.pitchScales = Arrays.copyOf(this.pitchScales, index * 2);
        }
        this.volumeScales[index] = volumeScale;
        this.pitchScales[index] = pitchScale;
        this.factories.add(factory);
        this.filters.add(filter);
    }

    public int size() {
        return this.factories.size();
    }

    public void clear() {
        this.factories.clear();
        this.filters.clear();
    }

    public void forEach(IAccentConsumer consumer) {
        for (int i = 0; i < this.factories.size(); i++)
            consumer.accept(this.factories.get(i), this.volumeScales[i], this.pitchScales[i], this.filters.get(i));
    }
}
