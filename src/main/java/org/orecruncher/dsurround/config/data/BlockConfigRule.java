package org.orecruncher.dsurround.config.data;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.block.state.BlockState;
import org.orecruncher.dsurround.lib.CodecExtensions;
import org.orecruncher.dsurround.lib.IMatcher;
import org.orecruncher.dsurround.lib.scripting.Script;

import java.util.List;
import java.util.Optional;

public record BlockConfigRule(
        List<IMatcher<BlockState>> blocks,
        Boolean clearSounds,
        Optional<Script> soundChance,
        List<AcousticConfig> acoustics,
        List<BlockEffectConfigRule> effects) {

        public static final Codec<BlockConfigRule> CODEC = RecordCodecBuilder.create((instance) -> instance.group(
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

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Blocks [\n");
        for (var matcher : this.blocks)
            builder.append("  ").append(matcher.toString()).append("\n");
        builder.append("]\n");
        builder.append("Clear Sounds: ").append(this.clearSounds).append("\n");
        builder.append("Sound Chance: ").append(this.soundChance.map(Script::asString).orElse("default")).append("\n");
        builder.append("Acoustics: [\n");
        for (var acoustic : this.acoustics)
            builder.append("  ").append(acoustic.toString()).append("\n");
        builder.append("]\n");
        builder.append("Block Effects: [\n");
        for (var effect : this.effects)
            builder.append("  ").append(effect.toString()).append("\n");
        builder.append("]\n");
        return builder.toString();
    }
}