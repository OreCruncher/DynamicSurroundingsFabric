package org.orecruncher.dsurround.config;

import com.google.common.base.MoreObjects;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.lib.weighted.WeightTable;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.scripting.Script;
import org.orecruncher.dsurround.lib.weighted.WeightValue;
import org.orecruncher.dsurround.runtime.IConditionEvaluator;
import org.orecruncher.dsurround.sound.ISoundFactory;

public class AcousticEntry extends WeightTable.Entry<ISoundFactory> {

    private static final IConditionEvaluator CONDITION_EVALUATOR = ContainerManager.resolve(IConditionEvaluator.class);
    private static final WeightValue DEFAULT_WEIGHT = WeightValue.of(10);

    private final Script conditions;

    public AcousticEntry(final ISoundFactory acoustic, @Nullable final Script condition) {
        this(acoustic, condition, DEFAULT_WEIGHT);
    }

    public AcousticEntry(final ISoundFactory acoustic, @Nullable final Script condition, final WeightValue weight) {
        super(acoustic, weight);
        this.conditions = condition != null ? condition : Script.TRUE;
    }

    public ISoundFactory getAcoustic() {
        return this.data();
    }

    public Script getConditions() {
        return this.conditions;
    }

    public boolean matches() {
        return this.conditions == Script.TRUE || CONDITION_EVALUATOR.check(this.conditions);
    }

    protected Script getConditionsForLogging() {
        return getConditions();
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
        return MoreObjects.toStringHelper(this)
                .addValue(weight())
                .addValue(getAcoustic().toString())
                .addValue(getConditionsForLogging())
                .toString();
    }
}