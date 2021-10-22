package org.orecruncher.dsurround.effects.entity.producers;

import com.google.common.collect.ImmutableList;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.orecruncher.dsurround.effects.IEntityEffect;
import org.orecruncher.dsurround.effects.entity.ToolbarEffect;

import java.util.Collection;

@Environment(EnvType.CLIENT)
public class ToolbarEffectProducer extends EntityEffectProducer {
    public Collection<IEntityEffect> produce(LivingEntity entity) {
        if (entity instanceof PlayerEntity)
            return ImmutableList.of(new ToolbarEffect());
        return ImmutableList.of();
    }
}
