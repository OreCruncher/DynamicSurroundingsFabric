package org.orecruncher.dsurround.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.sound.SoundCategory;
import org.orecruncher.dsurround.lib.scripting.Script;

@Environment(EnvType.CLIENT)
public class AcousticConfig {

    private static final Codec<SoundCategory> SOUND_CATEGORY_CODEC = Codec.STRING.xmap(AcousticConfig::lookup, SoundCategory::getName);

    public static Codec<AcousticConfig> CODEC = RecordCodecBuilder.create((instance) ->
            instance.group(
                Codec.STRING.fieldOf("soundEventId").forGetter(info -> info.soundEventId),
                Script.CODEC.optionalFieldOf("conditions", Script.TRUE).forGetter(info -> info.conditions),
                Codec.intRange(0, Integer.MIN_VALUE).optionalFieldOf("weight", 10).forGetter(info -> info.weight),
                SOUND_CATEGORY_CODEC.optionalFieldOf("category", SoundCategory.AMBIENT).forGetter(info -> info.category),
                Codec.FLOAT.optionalFieldOf("minVolume", 1F).forGetter(info -> info.minVolume),
                Codec.FLOAT.optionalFieldOf("maxVolume", 1F).forGetter(info -> info.maxVolume),
                Codec.FLOAT.optionalFieldOf("minPitch", 1F).forGetter(info -> info.minPitch),
                Codec.FLOAT.optionalFieldOf("maxPitch", 1F).forGetter(info -> info.maxPitch),
                SoundEventType.CODEC.optionalFieldOf("type", SoundEventType.LOOP).forGetter(info -> info.type)
            ).apply(instance, AcousticConfig::new));

    private static SoundCategory lookup(String string) {
        for (var c : SoundCategory.values())
            if (c.getName().equals(string))
                return c;
        return SoundCategory.AMBIENT;
    }

    public String soundEventId;
    public Script conditions;
    public int weight;
    public SoundCategory category;
    public float minVolume;
    public float maxVolume;
    public float minPitch;
    public float maxPitch;
    public SoundEventType type;

    AcousticConfig(String soundEventId, Script conditions, Integer weight, SoundCategory category, Float minVolume, Float maxVolume, Float minPitch, Float maxPitch, SoundEventType type) {
        this.soundEventId = soundEventId;
        this.conditions = conditions;
        this.weight = weight;
        this.category = category;
        this.minVolume = minVolume;
        this.maxVolume = maxVolume;
        this.minPitch = minPitch;
        this.maxPitch = maxPitch;
        this.type = type;
    }
}