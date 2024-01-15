package org.orecruncher.dsurround.config.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.minecraft.util.valueproviders.FloatProvider;
import org.orecruncher.dsurround.config.SoundEventType;
import org.orecruncher.dsurround.lib.IdentityUtils;
import org.orecruncher.dsurround.lib.scripting.Script;
import org.orecruncher.dsurround.sound.SoundCodecHelpers;

public record AcousticConfig(
        ResourceLocation soundEventId,
        Script conditions,
        Integer weight,
        SoundSource category,
        FloatProvider volume,
        FloatProvider pitch,
        SoundEventType type) {

    public static Codec<AcousticConfig> CODEC = RecordCodecBuilder.create((instance) ->
            instance.group(
                IdentityUtils.CODEC.fieldOf("soundEventId").forGetter(AcousticConfig::soundEventId),
                Script.CODEC.optionalFieldOf("conditions", Script.TRUE).forGetter(AcousticConfig::conditions),
                Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("weight", 10).forGetter(AcousticConfig::weight),
                SoundCodecHelpers.SOUND_CATEGORY_CODEC.optionalFieldOf("category", SoundSource.AMBIENT).forGetter(AcousticConfig::category),
                FloatProvider.CODEC.optionalFieldOf("volume", ConstantFloat.of(1F)).forGetter(AcousticConfig::volume),
                FloatProvider.CODEC.optionalFieldOf("pitch", ConstantFloat.of(1F)).forGetter(AcousticConfig::pitch),
                SoundEventType.CODEC.optionalFieldOf("type", SoundEventType.LOOP).forGetter(AcousticConfig::type)
            ).apply(instance, AcousticConfig::new));
}