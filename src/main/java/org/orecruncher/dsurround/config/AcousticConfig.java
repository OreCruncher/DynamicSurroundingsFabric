package org.orecruncher.dsurround.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class AcousticConfig {

    public static Codec<AcousticConfig> CODEC = RecordCodecBuilder.create((instance) ->
            instance.group(
                    Codec.STRING.fieldOf("soundEventId").forGetter(info -> info.soundEventId),
                    Codec.STRING.optionalFieldOf("conditions", "").forGetter(info -> info.conditions),
                    Codec.intRange(0, Integer.MIN_VALUE).optionalFieldOf("weight", 10).forGetter(info -> info.weight),
                    SoundEventType.CODEC.optionalFieldOf("type", SoundEventType.LOOP).forGetter(info -> info.type)
            ).apply(instance, AcousticConfig::new));

    public String soundEventId;
    public String conditions;
    public int weight;
    public SoundEventType type;

    AcousticConfig(String soundEventId, String conditions, int weight, SoundEventType type) {
        this.soundEventId = soundEventId;
        this.conditions = conditions;
        this.weight = weight;
        this.type = type;
    }
}