package org.orecruncher.dsurround.config.data;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.lib.CodecExtensions;
import org.orecruncher.dsurround.lib.IMatcher;
import org.orecruncher.dsurround.lib.IdentityUtils;

import java.util.List;
import java.util.Optional;

public record SoundMappingConfigRule(ResourceLocation soundEvent, List<MappingRule> rules) {

    public static final Codec<SoundMappingConfigRule> CODEC = RecordCodecBuilder.create((instance) ->
            instance.group(
                    IdentityUtils.CODEC.fieldOf("soundEvent").forGetter(SoundMappingConfigRule::soundEvent),
                    Codec.list(MappingRule.CODEC).fieldOf("rules").forGetter(SoundMappingConfigRule::rules)
            ).apply(instance, SoundMappingConfigRule::new));

    public Optional<ResourceLocation> findMatch(@Nullable BlockState state) {
        Optional<ResourceLocation> factory = Optional.empty();
        for (var rule : this.rules) {
            factory = rule.findMatch(state);
            if (factory.isPresent())
                break;
        }
        return factory;
    }

    public boolean isBlockStateNeeded() {
        return !this.rules.isEmpty() && !this.rules.getFirst().blocks.isEmpty();
    }

    public record MappingRule(List<IMatcher<BlockState>> blocks, ResourceLocation factory) {

        public static final Codec<MappingRule> CODEC = RecordCodecBuilder.create((instance) ->
                instance.group(
                        Codec.list(CodecExtensions.checkBlockStateSpecification(true)).optionalFieldOf("blocks", ImmutableList.of()).forGetter(MappingRule::blocks),
                        IdentityUtils.CODEC.fieldOf("factory").forGetter(MappingRule::factory)).apply(instance, MappingRule::new));

            public Optional<ResourceLocation> findMatch(@Nullable BlockState state) {
                if (this.blocks.isEmpty())
                    return Optional.of(this.factory);
                if (state == null)
                    throw new RuntimeException("BlockState needs to be specified in order to match blockstate rules");
                for( var rule : this.blocks) {
                    if (rule.match(state))
                        return Optional.of(this.factory);
                }
                return Optional.empty();
            }
    }
}
