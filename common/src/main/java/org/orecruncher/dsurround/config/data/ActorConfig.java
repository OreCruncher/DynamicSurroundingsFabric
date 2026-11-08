// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.config.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.NotNull;
import net.minecraft.resources.Identifier;
import org.orecruncher.dsurround.lib.scripting.Script;

/**
 * A world actor a biome gives, from a biome entry's {@code actors} list.
 *
 * @param actor      what kind ({@code dsurround:dust_devil}); one the mod doesn't know is left out, with a warning
 * @param conditions when it can appear
 * @param chance     the chance, 0 to 1, of one starting in a stretch of the biome (128 blocks square) in a minute; 0
 *                   takes away one an earlier entry gave
 */
public record ActorConfig(Identifier actor, Script conditions, float chance) {

    private static final float DEFAULT_CHANCE = 0.03F;

    public static final Codec<ActorConfig> CODEC = RecordCodecBuilder.create((instance) ->
            instance.group(
                    Identifier.CODEC.fieldOf("actor").forGetter(ActorConfig::actor),
                    Script.CODEC.optionalFieldOf("conditions", Script.TRUE).forGetter(ActorConfig::conditions),
                    Codec.floatRange(0F, 1F).optionalFieldOf("chance", DEFAULT_CHANCE).forGetter(ActorConfig::chance)
            ).apply(instance, ActorConfig::new));

    @Override
    public @NotNull String toString() {
        return "%s (chance %s, when %s)".formatted(this.actor, this.chance, this.conditions.asString());
    }
}
