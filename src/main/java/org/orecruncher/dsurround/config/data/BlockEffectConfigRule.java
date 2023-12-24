package org.orecruncher.dsurround.config.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.orecruncher.dsurround.config.BlockEffectType;
import org.orecruncher.dsurround.lib.scripting.Script;

public record BlockEffectConfigRule(
        BlockEffectType effect,
        Script conditions,
        Script spawnChance,
        Boolean alwaysOn) {

    private static final Script DEFAULT_SPAWN_CHANCE = new Script("0.01");

    public static Codec<BlockEffectConfigRule> CODEC = RecordCodecBuilder.create((instance) -> instance.group(
                    BlockEffectType.CODEC.fieldOf("effect").forGetter(BlockEffectConfigRule::effect),
                    Script.CODEC.optionalFieldOf("conditions", Script.TRUE).forGetter(BlockEffectConfigRule::conditions),
                    Script.CODEC.optionalFieldOf("spawnChance", DEFAULT_SPAWN_CHANCE).forGetter(BlockEffectConfigRule::spawnChance),
                    Codec.BOOL.optionalFieldOf("alwaysOn", false).forGetter(BlockEffectConfigRule::alwaysOn))
            .apply(instance, BlockEffectConfigRule::new));
}
