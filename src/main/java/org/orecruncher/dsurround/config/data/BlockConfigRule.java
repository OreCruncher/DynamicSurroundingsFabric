package org.orecruncher.dsurround.config.data;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import org.orecruncher.dsurround.config.AcousticConfig;
import org.orecruncher.dsurround.lib.CodecExtensions;
import org.orecruncher.dsurround.lib.block.BlockStateMatcher;
import org.orecruncher.dsurround.lib.scripting.Script;

import java.util.List;
import java.util.Optional;

@Environment(EnvType.CLIENT)
public class BlockConfigRule {

    public static Codec<BlockConfigRule> CODEC = RecordCodecBuilder.create((instance) ->
            instance.group(
                    Codec.list(CodecExtensions.checkBlockStateSpecification(true, true)).fieldOf("blocks").forGetter(info -> info.blocks),
                    Codec.BOOL.optionalFieldOf("clearSounds", false).forGetter(info -> info.clearSounds),
                    Script.CODEC.optionalFieldOf("soundChance").forGetter(info -> info.soundChance),
                    Codec.list(AcousticConfig.CODEC).optionalFieldOf("acoustics", ImmutableList.of()).forGetter(info -> info.acoustics),
                    Codec.list(BlockEffectConfig.CODEC).optionalFieldOf("effects", ImmutableList.of()).forGetter(info -> info.effects)
            ).apply(instance, BlockConfigRule::new));

    public List<BlockStateMatcher> blocks;
    public boolean clearSounds;
    public Optional<Script> soundChance;
    public List<AcousticConfig> acoustics;
    public List<BlockEffectConfig> effects;

    BlockConfigRule(List<BlockStateMatcher> blocks, boolean soundReset, Optional<Script> chance, List<AcousticConfig> acoustics, List<BlockEffectConfig> effects) {
        this.blocks = blocks;
        this.clearSounds = soundReset;
        this.soundChance = chance;
        this.acoustics = acoustics;
        this.effects = effects;
    }

    public boolean match(BlockState state) {
        for (var rule : this.blocks)
            if (rule.match(state))
                return true;
        return false;
    }
}