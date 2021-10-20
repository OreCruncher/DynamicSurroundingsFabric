package org.orecruncher.dsurround.config.biome;

import com.google.common.base.MoreObjects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.lib.WeightTable;
import org.orecruncher.dsurround.lib.scripting.Script;
import org.orecruncher.dsurround.runtime.ConditionEvaluator;
import org.orecruncher.dsurround.sound.ISoundFactory;

@Environment(EnvType.CLIENT)
public class AcousticEntry implements WeightTable.IItem<ISoundFactory> {

    private static final int DEFAULT_WEIGHT = 10;

    private final int weight;
    private final ISoundFactory acoustic;
    private final Script conditions;

    public AcousticEntry(final ISoundFactory acoustic, @Nullable final Script condition) {
        this(acoustic, condition, DEFAULT_WEIGHT);
    }

    public AcousticEntry(final ISoundFactory acoustic, @Nullable final Script condition, int weight) {
        this.acoustic = acoustic;
        this.weight = weight;
        this.conditions = condition != null ? condition : Script.TRUE;
    }

    @Override
    public int getWeight() {
        return this.weight;
    }

    @Override
    public ISoundFactory getItem() {
        return getAcoustic();
    }

    public ISoundFactory getAcoustic() {
        return this.acoustic;
    }

    public Script getConditions() {
        return this.conditions;
    }

    public boolean matches() {
        return ConditionEvaluator.INSTANCE.check(this.conditions);
    }

    protected Script getConditionsForLogging() {
        return getConditions();
    }

    public String toString() {
        return MoreObjects.toStringHelper(this)
                .addValue(getWeight())
                .addValue(getAcoustic().toString())
                .addValue(getConditionsForLogging())
                .toString();
    }
}