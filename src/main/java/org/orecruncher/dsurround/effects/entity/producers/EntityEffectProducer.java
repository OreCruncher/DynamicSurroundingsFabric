package org.orecruncher.dsurround.effects.entity.producers;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.LivingEntity;
import org.orecruncher.dsurround.effects.IEntityEffect;
import org.orecruncher.dsurround.effects.IEntityEffectProducer;

import java.util.Collection;

@Environment(EnvType.CLIENT)
public class EntityEffectProducer implements IEntityEffectProducer {
    @Override
    public Collection<IEntityEffect> produce(LivingEntity entity) {
        return null;
    }
}
