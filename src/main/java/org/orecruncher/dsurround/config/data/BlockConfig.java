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
public class BlockConfig {

    public static Codec<BlockConfig> CODEC = RecordCodecBuilder.create((instance) ->
            instance.group(
                    Codec.list(CodecExtensions.checkBlockStateSpecification()).fieldOf("blocks").forGetter(info -> info.blocks),
                    Codec.BOOL.optionalFieldOf("soundReset", false).forGetter(info -> info.soundReset),
                    Codec.STRING.optionalFieldOf("chance").forGetter(info -> info.chance),
                    Codec.list(AcousticConfig.CODEC).optionalFieldOf("acoustics", ImmutableList.of()).forGetter(info -> info.acoustics)
            ).apply(instance, BlockConfig::new));

    public List<String> blocks;
    public boolean soundReset;
    public Optional<String> chance;
    public List<AcousticConfig> acoustics;

    BlockConfig(List<String> blocks, boolean soundReset, Optional<String> chance, List<AcousticConfig> acoustics) {
        this.blocks = blocks;
        this.soundReset = soundReset;
        this.chance = chance;
        this.acoustics = acoustics;
    }
}