// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.config;

import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.config.data.ActorConfig;
import org.orecruncher.dsurround.lib.scripting.Script;
import org.orecruncher.dsurround.runtime.IConditionEvaluator;

/**
 * A world actor a biome gives, as configured (see {@link ActorConfig}), with what decides whether it can appear now.
 */
public final class ActorEntry {

    private final ActorKind kind;
    private final Script conditions;
    private final float chance;
    private final IConditionEvaluator conditionEvaluator;

    public ActorEntry(ActorKind kind, ActorConfig config, IConditionEvaluator conditionEvaluator) {
        this.kind = kind;
        this.conditions = config.conditions();
        this.chance = config.chance();
        this.conditionEvaluator = conditionEvaluator;
    }

    public ActorKind kind() {
        return this.kind;
    }

    /**
     * The chance, 0 to 1, of one starting in a 128 block square of the biome in a minute
     */
    public float chance() {
        return this.chance;
    }

    public boolean matches() {
        return this.conditions == Script.TRUE || this.conditionEvaluator.check(this.conditions);
    }

    @Override
    public @NotNull String toString() {
        return "%s (chance %s, when %s)".formatted(this.kind.getId(), this.chance, this.conditions.asString());
    }
}
