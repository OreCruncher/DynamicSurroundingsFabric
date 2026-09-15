package org.orecruncher.dsurround.config.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.random.Weight;
import org.orecruncher.dsurround.config.SoundEventType;
import org.orecruncher.dsurround.lib.IdentityUtils;
import org.orecruncher.dsurround.lib.scripting.Script;
import org.orecruncher.dsurround.lib.weighted.WeightValue;

public record AcousticConfig(
        ResourceLocation factory,
        Script conditions,
        WeightValue weight,
        SoundEventType type) {

    private static final WeightValue DEFAULT_WEIGHT = WeightValue.of(10);

    public static final Codec<AcousticConfig> CODEC = RecordCodecBuilder.create((instance) ->
            instance.group(
                IdentityUtils.CODEC.fieldOf("factory").forGetter(AcousticConfig::factory),
                Script.CODEC.optionalFieldOf("conditions", Script.TRUE).forGetter(AcousticConfig::conditions),
                WeightValue.CODEC.optionalFieldOf("weight", DEFAULT_WEIGHT).forGetter(AcousticConfig::weight),
                SoundEventType.CODEC.optionalFieldOf("type", SoundEventType.LOOP).forGetter(AcousticConfig::type)
            ).apply(instance, AcousticConfig::new));
}