package org.orecruncher.dsurround.config.biome;

import com.google.common.base.MoreObjects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.sound.SoundEvent;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.lib.WeightTable;
import org.orecruncher.dsurround.runtime.ConditionEvaluator;

@Environment(EnvType.CLIENT)
public class AcousticEntry implements WeightTable.IItem<SoundEvent> {

    private static final int DEFAULT_WEIGHT = 10;

    private final int weight;
    private final SoundEvent acoustic;
    private final String conditions;

    public AcousticEntry(final SoundEvent acoustic, @Nullable final String condition) {
        this(acoustic, condition, DEFAULT_WEIGHT);
    }

    public AcousticEntry(final SoundEvent acoustic, @Nullable final String condition, int weight) {
        this.acoustic = acoustic;
        this.weight = weight;
        this.conditions = condition != null ? condition : StringUtils.EMPTY;
    }

    @Override
    public int getWeight() {
        return this.weight;
    }

    @Override
    public SoundEvent getItem() {
        return getAcoustic();
    }

    public SoundEvent getAcoustic() {
        return this.acoustic;
    }

    public String getConditions() {
        return this.conditions;
    }

    public boolean matches() {
        return ConditionEvaluator.INSTANCE.check(this.conditions);
    }

    protected String getConditionsForLogging() {
        final String cond = getConditions();
        return cond.length() > 0 ? cond : "No Conditions";
    }

    public String toString() {
        return MoreObjects.toStringHelper(this)
                .addValue(getWeight())
                .addValue(getAcoustic().toString())
                .addValue(getConditionsForLogging())
                .toString();
    }
}