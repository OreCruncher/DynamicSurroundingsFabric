package org.orecruncher.dsurround.config.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.orecruncher.dsurround.config.BlockEffectType;
import org.orecruncher.dsurround.lib.scripting.Script;

@Environment(EnvType.CLIENT)
public class BlockEffectConfig {

    private static final Script DEFAULT_SPAWN_CHANCE = new Script("0.01");
    private static final Script ALWAYS_ON = new Script("1.0");

    public static Codec<BlockEffectConfig> CODEC = RecordCodecBuilder.create((instance) ->
            instance.group(
                    BlockEffectType.CODEC.fieldOf("effect").forGetter(info -> info.effect),
                    Script.CODEC.optionalFieldOf("conditions", Script.TRUE).forGetter(info -> info.conditions),
                    Script.CODEC.optionalFieldOf("spawnChance", DEFAULT_SPAWN_CHANCE).forGetter(info -> info.spawnChance),
                    Codec.BOOL.optionalFieldOf("alwaysOn", false).forGetter(info -> info.alwaysOn)
            ).apply(instance, BlockEffectConfig::new));

    public final BlockEffectType effect;
    public final Script conditions;
    public final Script spawnChance;
    public final boolean alwaysOn;

    BlockEffectConfig(BlockEffectType type, Script conditions, Script chance, boolean alwaysOn) {
        this.effect = type;
        this.conditions = conditions;
        this.spawnChance = alwaysOn ? ALWAYS_ON : chance;
        this.alwaysOn = alwaysOn;
    }
}
