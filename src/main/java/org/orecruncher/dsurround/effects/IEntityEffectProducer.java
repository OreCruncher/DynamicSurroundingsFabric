package org.orecruncher.dsurround.effects;

import net.minecraft.world.entity.LivingEntity;

import java.util.Collection;

@FunctionalInterface
public interface IEntityEffectProducer {
    /**
     * Obtains a list of zero or more entity effects that apply to the entity
     */
    Collection<IEntityEffect> produce(LivingEntity entity);
}
