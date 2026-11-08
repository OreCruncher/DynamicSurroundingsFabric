// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.config;

import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.config.data.ActorConfig;
import org.orecruncher.dsurround.lib.scripting.Script;
import org.orecruncher.dsurround.runtime.IConditionEvaluator;

/**
 * A world actor a biome gives, as configured (see {@link ActorConfig}), with what decides whether it can appear now.
 */
public final class ActorEntry {

    private final ActorKind kind;
    private final Script conditions;
    private final Script chance;
    // The chance, when it is a number rather than a script
    @Nullable
    private final Float constantChance;
    private final IConditionEvaluator conditionEvaluator;

    public ActorEntry(ActorKind kind, ActorConfig config, IConditionEvaluator conditionEvaluator) {
        this.kind = kind;
        this.conditions = config.conditions();
        this.chance = config.chance();
        this.constantChance = ActorConfig.constant(config.chance());
        this.conditionEvaluator = conditionEvaluator;
    }

    public ActorKind kind() {
        return this.kind;
    }

    /**
     * The chance now, 0 to 1, of one starting in a 128 block square of the biome in a minute: a script's that gives no
     * number is none
     */
    public float chance() {
        if (this.constantChance != null)
            return Mth.clamp(this.constantChance, 0F, 1F);
        return this.conditionEvaluator.eval(this.chance) instanceof Number number ? Mth.clamp(number.floatValue(), 0F, 1F) : 0F;
    }

    public boolean matches() {
        return this.conditions == Script.TRUE || this.conditionEvaluator.check(this.conditions);
    }

    @Override
    public @NotNull String toString() {
        return "%s (chance %s, when %s)".formatted(this.kind.getId(), this.chance.asString(), this.conditions.asString());
    }
}
