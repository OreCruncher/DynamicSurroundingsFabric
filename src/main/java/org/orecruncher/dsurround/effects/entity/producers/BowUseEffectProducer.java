package org.orecruncher.dsurround.effects.entity.producers;

import com.google.common.collect.ImmutableList;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.LivingEntity;
import org.orecruncher.dsurround.effects.IEntityEffect;
import org.orecruncher.dsurround.effects.entity.BowUseEffect;

import java.util.Collection;

@Environment(EnvType.CLIENT)
public class BowUseEffectProducer extends EntityEffectProducer {
    public Collection<IEntityEffect> produce(LivingEntity entity) {
        return ImmutableList.of(new BowUseEffect());
    }
}
