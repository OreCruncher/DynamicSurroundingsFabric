package org.orecruncher.dsurround.config;

import com.google.common.base.MoreObjects;
import net.minecraft.sound.SoundEvent;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.lib.WeightTable;

public class WeightedAcousticEntry extends AcousticEntry implements WeightTable.IItem<SoundEvent> {

    private final int weight;

    public WeightedAcousticEntry(final SoundEvent acoustic, @Nullable String conditions, final int weight) {
        super(acoustic, conditions);
        this.weight = weight;
    }

    @Override
    public int getWeight() {
        return this.weight;
    }

    @Override
    public SoundEvent getItem() {
        return getAcoustic();
    }

    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("weight", getWeight())
                .addValue(getItem().toString())
                .addValue(getConditionsForLogging())
                .toString();
    }
}