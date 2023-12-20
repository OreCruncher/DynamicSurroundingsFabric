package org.orecruncher.dsurround.config.data;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import org.orecruncher.dsurround.config.AcousticConfig;
import org.orecruncher.dsurround.lib.CodecExtensions;
import org.orecruncher.dsurround.lib.IMatcher;
import org.orecruncher.dsurround.lib.scripting.Script;

import java.util.List;
import java.util.Optional;

@Environment(EnvType.CLIENT)
public record BlockConfigRule(
        List<IMatcher<BlockState>> blocks,
        Boolean clearSounds,
        Optional<Script> soundChance,
        List<AcousticConfig> acoustics,
        List<BlockEffectConfigRule> effects) {

        public static Codec<BlockConfigRule> CODEC = RecordCodecBuilder.create((instance) -> instance.group(
                    Codec.list(CodecExtensions.checkBlockStateSpecification(true)).fieldOf("blocks")
                            .forGetter(BlockConfigRule::blocks),
                    Codec.BOOL.optionalFieldOf("clearSounds", false).forGetter(BlockConfigRule::clearSounds),
                    Script.CODEC.optionalFieldOf("soundChance").forGetter(BlockConfigRule::soundChance),
                    Codec.list(AcousticConfig.CODEC).optionalFieldOf("acoustics", ImmutableList.of())
                            .forGetter(BlockConfigRule::acoustics),
                    Codec.list(BlockEffectConfigRule.CODEC).optionalFieldOf("effects", ImmutableList.of())
                            .forGetter(BlockConfigRule::effects))
            .apply(instance, BlockConfigRule::new));

    public boolean match(BlockState state) {
        for (var rule : this.blocks)
            if (rule.match(state))
                return true;
        return false;
    }
}