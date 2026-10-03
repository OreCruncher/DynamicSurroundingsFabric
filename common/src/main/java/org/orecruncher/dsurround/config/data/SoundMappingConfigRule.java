package org.orecruncher.dsurround.config.data;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
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

    /**
     * Rejects a mapping with no rules, or with a default rule (one without blocks) anywhere but last, or with more
     * than one: rules are tried in order and merging inserts new ones before the default, so these would make rules
     * unreachable or fail when merged.
     */
    public static final Codec<SoundMappingConfigRule> CODEC = RecordCodecBuilder.<SoundMappingConfigRule>create((instance) ->
            instance.group(
                    IdentityUtils.CODEC.fieldOf("soundEvent").forGetter(SoundMappingConfigRule::soundEvent),
                    Codec.list(MappingRule.CODEC).fieldOf("rules").forGetter(SoundMappingConfigRule::rules)
            ).apply(instance, SoundMappingConfigRule::new))
            .validate(SoundMappingConfigRule::validate);

    private static DataResult<SoundMappingConfigRule> validate(SoundMappingConfigRule rule) {
        var rules = rule.rules();
        if (rules.isEmpty())
            return DataResult.error(() -> "Sound mapping for %s has no rules".formatted(rule.soundEvent()));
        for (int i = 0; i < rules.size() - 1; i++) {
            if (rules.get(i).isDefault())
                return DataResult.error(() -> "Sound mapping for %s: the rule without blocks (the default) must be the only one and last"
                        .formatted(rule.soundEvent()));
        }
        return DataResult.success(rule);
    }

    public record MappingRule(List<IMatcher<BlockState>> blocks, ResourceLocation factory) {

        /**
         * A rule without blocks applies to any block state: the mapping's default.
         */
        public boolean isDefault() {
            return this.blocks.isEmpty();
        }

        public static final Codec<MappingRule> CODEC = RecordCodecBuilder.create((instance) ->
                instance.group(
                        Codec.list(CodecExtensions.checkBlockStateSpecification(true)).optionalFieldOf("blocks", ImmutableList.of()).forGetter(MappingRule::blocks),
                        IdentityUtils.CODEC.fieldOf("factory").forGetter(MappingRule::factory)).apply(instance, MappingRule::new));
    }
}
