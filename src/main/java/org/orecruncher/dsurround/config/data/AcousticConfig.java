package org.orecruncher.dsurround.config.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import org.orecruncher.dsurround.config.SoundEventType;
import org.orecruncher.dsurround.lib.IdentityUtils;
import org.orecruncher.dsurround.lib.scripting.Script;

public record AcousticConfig(
        ResourceLocation soundEventId,
        Script conditions,
        Integer weight,
        SoundSource category,
        Float minVolume,
        Float maxVolume,
        Float minPitch,
        Float maxPitch,
        SoundEventType type) {

    private static final Codec<SoundSource> SOUND_CATEGORY_CODEC = Codec.STRING.xmap(AcousticConfig::lookup, SoundSource::getName);

    public static Codec<AcousticConfig> CODEC = RecordCodecBuilder.create((instance) ->
            instance.group(
                IdentityUtils.CODEC.fieldOf("soundEventId").forGetter(AcousticConfig::soundEventId),
                Script.CODEC.optionalFieldOf("conditions", Script.TRUE).forGetter(AcousticConfig::conditions),
                Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("weight", 10).forGetter(AcousticConfig::weight),
                SOUND_CATEGORY_CODEC.optionalFieldOf("category", SoundSource.AMBIENT).forGetter(AcousticConfig::category),
                Codec.FLOAT.optionalFieldOf("minVolume", 1F).forGetter(AcousticConfig::minVolume),
                Codec.FLOAT.optionalFieldOf("maxVolume", 1F).forGetter(AcousticConfig::maxVolume),
                Codec.FLOAT.optionalFieldOf("minPitch", 1F).forGetter(AcousticConfig::minPitch),
                Codec.FLOAT.optionalFieldOf("maxPitch", 1F).forGetter(AcousticConfig::maxPitch),
                SoundEventType.CODEC.optionalFieldOf("type", SoundEventType.LOOP).forGetter(AcousticConfig::type)
            ).apply(instance, AcousticConfig::new));

    private static SoundSource lookup(String string) {
        for (var c : SoundSource.values())
            if (c.getName().equalsIgnoreCase(string))
                return c;
        return SoundSource.AMBIENT;
    }
}