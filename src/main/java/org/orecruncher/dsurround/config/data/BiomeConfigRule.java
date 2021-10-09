package org.orecruncher.dsurround.config.data;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.orecruncher.dsurround.config.AcousticConfig;
import org.orecruncher.dsurround.lib.CodecExtensions;

import java.util.List;
import java.util.Optional;

@Environment(EnvType.CLIENT)
public final class BiomeConfigRule {

    public static Codec<BiomeConfigRule> CODEC = RecordCodecBuilder.create((instance) ->
            instance.group(
                    Codec.STRING.fieldOf("biomeSelector").forGetter(info -> info.biomeSelector),
                    Codec.STRING.optionalFieldOf("_comment").forGetter(info -> info.comment),
                    Codec.BOOL.optionalFieldOf("clearSounds", false).forGetter(info -> info.clearSounds),
                    CodecExtensions.checkHTMLColor().optionalFieldOf("fogColor").forGetter(info -> info.fogColor),
                    Codec.STRING.optionalFieldOf("additionalSoundChance").forGetter(info -> info.additionalSoundChance),
                    Codec.STRING.optionalFieldOf("moodSoundChance").forGetter(info -> info.moodSoundChance),
                    Codec.list(AcousticConfig.CODEC).optionalFieldOf("acoustics", ImmutableList.of()).forGetter(info -> info.acoustics)
            ).apply(instance, BiomeConfigRule::new));

    public String biomeSelector;
    public Optional<String> comment;
    public boolean clearSounds;
    public Optional<String> fogColor;
    public Optional<String> additionalSoundChance;
    public Optional<String> moodSoundChance;
    public List<AcousticConfig> acoustics;

    BiomeConfigRule(String biomeSelector, Optional<String> comment, boolean clearSounds, Optional<String> fogColor, Optional<String> additionalSoundChance, Optional<String> moodSoundChance, List<AcousticConfig> acoustics) {
        this.biomeSelector = biomeSelector;
        this.comment = comment;
        this.clearSounds = clearSounds;
        this.fogColor = fogColor;
        this.additionalSoundChance = additionalSoundChance;
        this.moodSoundChance = moodSoundChance;
        this.acoustics = acoustics;
    }
}