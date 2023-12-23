package org.orecruncher.dsurround.config.data;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.text.TextColor;
import org.orecruncher.dsurround.config.AcousticConfig;
import org.orecruncher.dsurround.lib.scripting.Script;

import java.util.List;
import java.util.Optional;

@Environment(EnvType.CLIENT)
public record BiomeConfigRule(
        Script biomeSelector,
        Optional<String> comment,
        Boolean clearSounds,
        Optional<TextColor> fogColor,
        Optional<Script> additionalSoundChance,
        Optional<Script> moodSoundChance,
        List<AcousticConfig> acoustics) {

        public static Codec<BiomeConfigRule> CODEC = RecordCodecBuilder.create((instance) -> instance.group(
                        Script.CODEC.fieldOf("biomeSelector").forGetter(BiomeConfigRule::biomeSelector),
                        Codec.STRING.optionalFieldOf("_comment").forGetter(BiomeConfigRule::comment),
                        Codec.BOOL.optionalFieldOf("clearSounds", false).forGetter(BiomeConfigRule::clearSounds),
                        TextColor.CODEC.optionalFieldOf("fogColor").forGetter(BiomeConfigRule::fogColor),
                        Script.CODEC.optionalFieldOf("additionalSoundChance").forGetter(BiomeConfigRule::additionalSoundChance),
                        Script.CODEC.optionalFieldOf("moodSoundChance").forGetter(BiomeConfigRule::moodSoundChance),
                        Codec.list(AcousticConfig.CODEC).optionalFieldOf("acoustics", ImmutableList.of()).forGetter(BiomeConfigRule::acoustics))
                .apply(instance, BiomeConfigRule::new));
}