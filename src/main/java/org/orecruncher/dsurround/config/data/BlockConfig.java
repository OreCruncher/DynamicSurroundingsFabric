package org.orecruncher.dsurround.config.data;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.orecruncher.dsurround.config.AcousticConfig;
import org.orecruncher.dsurround.lib.CodecExtensions;
import org.orecruncher.dsurround.lib.scripting.Script;

import java.util.List;
import java.util.Optional;

@Environment(EnvType.CLIENT)
public class BlockConfig {

    public static Codec<BlockConfig> CODEC = RecordCodecBuilder.create((instance) ->
            instance.group(
                    Codec.list(CodecExtensions.checkBlockStateSpecification()).fieldOf("blocks").forGetter(info -> info.blocks),
                    Codec.BOOL.optionalFieldOf("clearSounds", false).forGetter(info -> info.clearSounds),
                    Script.CODEC.optionalFieldOf("soundChance").forGetter(info -> info.soundChance),
                    Codec.list(AcousticConfig.CODEC).optionalFieldOf("acoustics", ImmutableList.of()).forGetter(info -> info.acoustics),
                    Codec.list(BlockEffectConfig.CODEC).optionalFieldOf("effects", ImmutableList.of()).forGetter(info -> info.effects)
            ).apply(instance, BlockConfig::new));

    public List<String> blocks;
    public boolean clearSounds;
    public Optional<Script> soundChance;
    public List<AcousticConfig> acoustics;
    public List<BlockEffectConfig> effects;

    BlockConfig(List<String> blocks, boolean soundReset, Optional<Script> chance, List<AcousticConfig> acoustics, List<BlockEffectConfig> effects) {
        this.blocks = blocks;
        this.clearSounds = soundReset;
        this.soundChance = chance;
        this.acoustics = acoustics;
        this.effects = effects;
    }
}