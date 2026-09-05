package org.orecruncher.dsurround.config.libraries;

import it.unimi.dsi.fastutil.ints.AbstractIntSet;
import net.minecraft.world.entity.LivingEntity;
import org.orecruncher.dsurround.effects.entity.EntityEffectInfo;

public interface IEntityEffectLibrary extends ILibrary {
    boolean doesEntityEffectInfoExist(LivingEntity entity);
    void cleanCache(AbstractIntSet entitiesToRetain);
    EntityEffectInfo getEntityEffectInfo(LivingEntity entity);
}
