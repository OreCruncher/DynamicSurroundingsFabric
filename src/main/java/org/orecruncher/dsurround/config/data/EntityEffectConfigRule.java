package org.orecruncher.dsurround.config.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.orecruncher.dsurround.config.EntityEffectType;
import org.orecruncher.dsurround.config.EntityTypeMatcher;
import org.orecruncher.dsurround.lib.IMatcher;

import java.util.List;

@Environment(EnvType.CLIENT)
public class EntityEffectConfigRule {

    public static Codec<EntityEffectConfigRule> CODEC = RecordCodecBuilder.create((instance) -> instance.group(
            Codec.list(EntityTypeMatcher.CODEC).fieldOf("entityTypes").forGetter(info -> info.entityTypeMatchers),
            Codec.list(EntityEffectType.CODEC).fieldOf("effects").forGetter(info -> info.effects))
            .apply(instance, EntityEffectConfigRule::new));

    public final List<IMatcher<Entity>> entityTypeMatchers;
    public final List<EntityEffectType> effects;

    EntityEffectConfigRule(List<IMatcher<Entity>> entityTypeMatchers, List<EntityEffectType> effects) {
        this.entityTypeMatchers = entityTypeMatchers;
        this.effects = effects;
    }

    public boolean match(LivingEntity entity) {
        for (var rule : this.entityTypeMatchers)
            if (rule.match(entity))
                return true;
        return false;
    }
}
