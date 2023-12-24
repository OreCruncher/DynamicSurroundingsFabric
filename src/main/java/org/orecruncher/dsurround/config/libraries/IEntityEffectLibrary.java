package org.orecruncher.dsurround.config.libraries;

import net.minecraft.entity.LivingEntity;
import org.orecruncher.dsurround.effects.entity.EntityEffectInfo;

public interface IEntityEffectLibrary extends ILibrary {
    boolean doesEntityEffectInfoExist(LivingEntity entity);
    void clearEntityEffectInfo(LivingEntity entity);
    EntityEffectInfo getEntityEffectInfo(LivingEntity entity);
}
