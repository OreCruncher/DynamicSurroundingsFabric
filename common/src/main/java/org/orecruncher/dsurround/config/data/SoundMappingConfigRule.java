package org.orecruncher.dsurround.config.data;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import org.orecruncher.dsurround.lib.CodecExtensions;
import org.orecruncher.dsurround.lib.IMatcher;
import org.orecruncher.dsurround.lib.IdentityUtils;

import java.util.List;

/**
 * Sound mapping configuration rule. The order of the rules is important as they are processed sequentially. For
 * mappings that have more than one rule, the default is placed as the last entry without any BlockState
 * specifications.
 */
public record SoundMappingConfigRule(ResourceLocation soundEvent, List<MappingRule> rules) {

    public static final Codec<SoundMappingConfigRule> CODEC = RecordCodecBuilder.create((instance) ->
            instance.group(
                    IdentityUtils.CODEC.fieldOf("soundEvent").forGetter(SoundMappingConfigRule::soundEvent),
                    Codec.list(MappingRule.CODEC).fieldOf("rules").forGetter(SoundMappingConfigRule::rules)
            ).apply(instance, SoundMappingConfigRule::new));

    public record MappingRule(List<IMatcher<BlockState>> blocks, ResourceLocation factory) {

        public static final Codec<MappingRule> CODEC = RecordCodecBuilder.create((instance) ->
                instance.group(
                        Codec.list(CodecExtensions.checkBlockStateSpecification(true)).optionalFieldOf("blocks", ImmutableList.of()).forGetter(MappingRule::blocks),
                        IdentityUtils.CODEC.fieldOf("factory").forGetter(MappingRule::factory)).apply(instance, MappingRule::new));
    }
}
