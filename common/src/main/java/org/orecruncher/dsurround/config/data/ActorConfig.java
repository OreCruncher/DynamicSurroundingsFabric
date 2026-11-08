// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.config.data;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.lib.scripting.Script;

/**
 * A world actor a biome gives, from a biome entry's {@code actors} list.
 *
 * @param actor      what kind ({@code dsurround:dust_devil}); one the mod doesn't know is left out, with a warning
 * @param conditions when it can appear
 * @param chance     the chance, 0 to 1, of one starting in a stretch of the biome (128 blocks square) in a minute: a
 *                   number, or a script giving one, for a chance that changes with the weather
 *                   ({@code "weather.isRaining() ? 0.03 : 0.005"}); 0 takes away one an earlier entry gave
 */
public record ActorConfig(Identifier actor, Script conditions, Script chance) {

    private static final Script DEFAULT_CHANCE = new Script("0.03");

    // A number from 0 to 1, or a script
    private static final Codec<Script> CHANCE_CODEC = Codec.either(Codec.floatRange(0F, 1F), Script.CODEC).xmap(
            either -> either.map(number -> new Script(Float.toString(number)), script -> script),
            script -> constant(script) instanceof Float number ? Either.left(number) : Either.right(script));

    public static final Codec<ActorConfig> CODEC = RecordCodecBuilder.create((instance) ->
            instance.group(
                    Identifier.CODEC.fieldOf("actor").forGetter(ActorConfig::actor),
                    Script.CODEC.optionalFieldOf("conditions", Script.TRUE).forGetter(ActorConfig::conditions),
                    CHANCE_CODEC.optionalFieldOf("chance", DEFAULT_CHANCE).forGetter(ActorConfig::chance)
            ).apply(instance, ActorConfig::new));

    /**
     * The chance as a number, if it is one rather than a script; null otherwise
     */
    public static @Nullable Float constant(Script chance) {
        try {
            return Float.parseFloat(chance.asString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Whether it takes the actor away: a chance of nothing
     */
    public boolean removes() {
        var number = constant(this.chance);
        return number != null && number <= 0F;
    }

    @Override
    public @NotNull String toString() {
        return "%s (chance %s, when %s)".formatted(this.actor, this.chance.asString(), this.conditions.asString());
    }
}
