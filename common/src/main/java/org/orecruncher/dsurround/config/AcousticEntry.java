// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.config;

import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.config.data.AcousticConfig;
import org.orecruncher.dsurround.lib.weighted.WeightTable;
import org.orecruncher.dsurround.lib.scripting.Script;
import org.orecruncher.dsurround.lib.weighted.WeightValue;
import org.orecruncher.dsurround.runtime.IConditionEvaluator;
import org.orecruncher.dsurround.sound.ISoundFactory;

public final class AcousticEntry extends WeightTable.Entry<ISoundFactory> {

    private static final WeightValue DEFAULT_WEIGHT = WeightValue.of(10);

    private final Script conditions;
    private final Script volume;
    private final IConditionEvaluator conditionEvaluator;

    /**
     * @param conditionEvaluator evaluates {@code condition}, to decide whether the entry can be chosen
     */
    public AcousticEntry(final ISoundFactory acoustic, @Nullable final Script condition, final IConditionEvaluator conditionEvaluator) {
        this(acoustic, condition, DEFAULT_WEIGHT, conditionEvaluator);
    }

    public AcousticEntry(final ISoundFactory acoustic, @Nullable final Script condition, final WeightValue weight, final IConditionEvaluator conditionEvaluator) {
        this(acoustic, condition, weight, AcousticConfig.FULL_VOLUME, conditionEvaluator);
    }

    /**
     * @param volume a script giving the loop's volume, 0 to 1 ({@link AcousticConfig#FULL_VOLUME} for always full)
     */
    public AcousticEntry(final ISoundFactory acoustic, @Nullable final Script condition, final WeightValue weight, final Script volume, final IConditionEvaluator conditionEvaluator) {
        super(acoustic, weight);
        this.conditions = condition != null ? condition : Script.TRUE;
        this.volume = volume;
        this.conditionEvaluator = conditionEvaluator;
    }

    /**
     * The loop's volume now, 0 to 1. A script that gives no number, or not a finite one, gives 0: a mistake silences
     * the loop rather than playing it at full volume
     */
    public float volume() {
        if (this.volume.equals(AcousticConfig.FULL_VOLUME))
            return 1F;
        if (this.conditionEvaluator.eval(this.volume) instanceof Number n) {
            double v = n.doubleValue();
            if (Double.isFinite(v))
                return (float) Math.clamp(v, 0D, 1D);
        }
        return 0F;
    }

    public ISoundFactory getAcoustic() {
        return this.data();
    }

    public Script getConditions() {
        return this.conditions;
    }

    public boolean matches() {
        return this.conditions == Script.TRUE || this.conditionEvaluator.check(this.conditions);
    }

    @Override
    public int hashCode() {
        return (this.conditions.hashCode() * 31 + this.volume.hashCode()) * 31 + this.data.getLocation().hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof AcousticEntry ae) {
            return ae.conditions.equals(this.conditions) && ae.volume.equals(this.volume)
                    && ae.data.getLocation().equals(this.data.getLocation());
        }
        return false;
    }

    public String toString() {
        var text = "Acoustic {%d, %s} %s".formatted(this.weight().asInt(), this.getAcoustic(), this.getConditions());
        return this.volume.equals(AcousticConfig.FULL_VOLUME) ? text : text + " at volume " + this.volume.asString();
    }
}