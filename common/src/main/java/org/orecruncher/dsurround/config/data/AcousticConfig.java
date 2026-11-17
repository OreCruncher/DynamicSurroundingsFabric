// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.config.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.config.SoundEventType;
import org.orecruncher.dsurround.lib.registry.IdentityUtils;
import org.orecruncher.dsurround.lib.scripting.Script;
import org.orecruncher.dsurround.lib.weighted.WeightValue;

public record AcousticConfig(
        Identifier factory,
        Script conditions,
        WeightValue weight,
        SoundEventType type,
        Script volume) {

    private static final WeightValue DEFAULT_WEIGHT = WeightValue.of(10);
    /**
     * A loop's volume, 0 to 1, as a script: evaluated each time the loops are gathered, so it can follow the player (a
     * blend by height, say). The other types ignore it
     */
    public static final Script FULL_VOLUME = new Script("1");

    public static final Codec<AcousticConfig> CODEC = RecordCodecBuilder.create((instance) ->
            instance.group(
                IdentityUtils.CODEC.fieldOf("factory").forGetter(AcousticConfig::factory),
                Script.CODEC.optionalFieldOf("conditions", Script.TRUE).forGetter(AcousticConfig::conditions),
                WeightValue.CODEC.optionalFieldOf("weight", DEFAULT_WEIGHT).forGetter(AcousticConfig::weight),
                SoundEventType.CODEC.optionalFieldOf("type", SoundEventType.LOOP).forGetter(AcousticConfig::type),
                Script.CODEC.optionalFieldOf("volume", FULL_VOLUME).forGetter(AcousticConfig::volume)
            ).apply(instance, AcousticConfig::new));

    public AcousticConfig(Identifier factory, Script conditions, WeightValue weight, SoundEventType type) {
        this(factory, conditions, weight, type, FULL_VOLUME);
    }

    @Override
    public @NotNull String toString() {
        var text = "%s (%s, weight %s, when %s)".formatted(this.factory, this.type.getName(), this.weight, this.conditions.asString());
        return this.volume.equals(FULL_VOLUME) ? text : text + " at volume " + this.volume.asString();
    }
}