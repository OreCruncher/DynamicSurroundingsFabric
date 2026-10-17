package org.orecruncher.dsurround.processing.accents;

import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.sound.ISoundFactory;

import java.util.Arrays;

/**
 * The accents gathered for a footstep: each a sound, and how loud to play it, as a share of the sound's own volume.
 * Reused from step to step.
 */
public final class StepAccents {

    private final ObjectArray<ISoundFactory> factories = new ObjectArray<>(4);
    private float[] volumeScales = new float[4];

    public interface IAccentConsumer {
        void accept(ISoundFactory factory, float volumeScale);
    }

    /**
     * Adds an accent at the sound's own volume
     */
    public void add(ISoundFactory factory) {
        this.add(factory, 1F);
    }

    public void add(ISoundFactory factory, float volumeScale) {
        int index = this.factories.size();
        if (index == this.volumeScales.length)
            this.volumeScales = Arrays.copyOf(this.volumeScales, index * 2);
        this.volumeScales[index] = volumeScale;
        this.factories.add(factory);
    }

    public int size() {
        return this.factories.size();
    }

    public void clear() {
        this.factories.clear();
    }

    public void forEach(IAccentConsumer consumer) {
        for (int i = 0; i < this.factories.size(); i++)
            consumer.accept(this.factories.get(i), this.volumeScales[i]);
    }
}
