package org.orecruncher.dsurround.config;

import com.google.common.base.MoreObjects;
import net.minecraft.util.random.Weight;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.lib.WeightTable;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.scripting.Script;
import org.orecruncher.dsurround.runtime.IConditionEvaluator;
import org.orecruncher.dsurround.sound.ISoundFactory;

public class AcousticEntry implements WeightTable.IItem<ISoundFactory> {

    private static final IConditionEvaluator CONDITION_EVALUATOR = ContainerManager.resolve(IConditionEvaluator.class);
    private static final Weight DEFAULT_WEIGHT = Weight.of(10);

    private final Weight weight;
    private final ISoundFactory acoustic;
    private final Script conditions;

    public AcousticEntry(final ISoundFactory acoustic, @Nullable final Script condition) {
        this(acoustic, condition, DEFAULT_WEIGHT);
    }

    public AcousticEntry(final ISoundFactory acoustic, @Nullable final Script condition, Weight weight) {
        this.acoustic = acoustic;
        this.weight = weight;
        this.conditions = condition != null ? condition : Script.TRUE;
    }

    @NotNull
    @Override
    public Weight getWeight() {
        return this.weight;
    }

    @Override
    public ISoundFactory data() {
        return getAcoustic();
    }

    public ISoundFactory getAcoustic() {
        return this.acoustic;
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
        return this.conditions.hashCode() * 31 + this.acoustic.getLocation().hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof AcousticEntry ae) {
            return ae.conditions.equals(this.conditions) && ae.acoustic.getLocation().equals(this.acoustic.getLocation());
        }
        return false;
    }

    public String toString() {
        return MoreObjects.toStringHelper(this)
                .addValue(getWeight())
                .addValue(getAcoustic().toString())
                .addValue(getConditionsForLogging())
                .toString();
    }
}