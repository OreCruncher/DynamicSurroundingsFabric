package org.orecruncher.dsurround.config;

import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.lib.weighted.WeightTable;
import org.orecruncher.dsurround.lib.scripting.Script;
import org.orecruncher.dsurround.lib.weighted.WeightValue;
import org.orecruncher.dsurround.runtime.IConditionEvaluator;
import org.orecruncher.dsurround.sound.ISoundFactory;

public final class AcousticEntry extends WeightTable.Entry<ISoundFactory> {

    private static final WeightValue DEFAULT_WEIGHT = WeightValue.of(10);

    private final Script conditions;
    private final IConditionEvaluator conditionEvaluator;

    /**
     * @param conditionEvaluator evaluates {@code condition}, to decide whether the entry can be chosen
     */
    public AcousticEntry(final ISoundFactory acoustic, @Nullable final Script condition, final IConditionEvaluator conditionEvaluator) {
        this(acoustic, condition, DEFAULT_WEIGHT, conditionEvaluator);
    }

    public AcousticEntry(final ISoundFactory acoustic, @Nullable final Script condition, final WeightValue weight, final IConditionEvaluator conditionEvaluator) {
        super(acoustic, weight);
        this.conditions = condition != null ? condition : Script.TRUE;
        this.conditionEvaluator = conditionEvaluator;
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
        return this.conditions.hashCode() * 31 + this.data.getLocation().hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof AcousticEntry ae) {
            return ae.conditions.equals(this.conditions) && ae.data.getLocation().equals(this.data.getLocation());
        }
        return false;
    }

    public String toString() {
        return "Acoustic {%d, %s} %s".formatted(this.weight().asInt(), this.getAcoustic(), this.getConditions());
    }
}