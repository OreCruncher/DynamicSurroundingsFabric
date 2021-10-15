package org.orecruncher.dsurround.config.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import org.orecruncher.dsurround.config.EntityEffectType;
import org.orecruncher.dsurround.config.EntityTypeMatcher;
import org.orecruncher.dsurround.lib.IMatcher;

import java.util.List;

@Environment(EnvType.CLIENT)
public class EntityEffectConfigRule {

    public static Codec<EntityEffectConfigRule> CODEC = RecordCodecBuilder.create((instance) ->
            instance.group(
                    Codec.list(EntityTypeMatcher.CODEC).fieldOf("entityTypes").forGetter(info -> info.entityTypeMatchers),
                    Codec.list(EntityEffectType.CODEC).fieldOf("effects").forGetter(info -> info.effects)
            ).apply(instance, EntityEffectConfigRule::new));

    private static DataResult<EntityType<?>> manifestEntityType(String entityTypeId) {
        try {
            var type = EntityType.get(entityTypeId);
            return type.<DataResult<EntityType<?>>>map(DataResult::success)
                    .orElseGet(() -> DataResult.error(String.format("Unknown entity type id %s", entityTypeId)));
        } catch (Throwable t) {
            return DataResult.error(t.getMessage());
        }
    }

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
