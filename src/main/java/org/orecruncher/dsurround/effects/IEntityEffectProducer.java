package org.orecruncher.dsurround.effects;

import net.minecraft.entity.LivingEntity;

import java.util.Collection;

@FunctionalInterface
public interface IEntityEffectProducer {
    /**
     * Obtains a list of 0 or more entity effects that apply to the entity
     */
    Collection<IEntityEffect> produce(LivingEntity entity);
}
