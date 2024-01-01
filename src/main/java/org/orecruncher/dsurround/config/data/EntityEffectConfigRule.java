package org.orecruncher.dsurround.config.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.orecruncher.dsurround.config.EntityEffectType;
import org.orecruncher.dsurround.config.EntityTypeMatcher;
import org.orecruncher.dsurround.lib.IMatcher;

import java.util.List;

public record EntityEffectConfigRule(
        List<IMatcher<Entity>> entityTypeMatchers,
        List<EntityEffectType> effects) {

    public static Codec<EntityEffectConfigRule> CODEC = RecordCodecBuilder.create((instance) -> instance.group(
            Codec.list(EntityTypeMatcher.CODEC).fieldOf("entityTypes").forGetter(EntityEffectConfigRule::entityTypeMatchers),
            Codec.list(EntityEffectType.CODEC).fieldOf("effects").forGetter(EntityEffectConfigRule::effects))
            .apply(instance, EntityEffectConfigRule::new));

    public boolean match(LivingEntity entity) {
        for (var rule : this.entityTypeMatchers)
            if (rule.match(entity))
                return true;
        return false;
    }
}
