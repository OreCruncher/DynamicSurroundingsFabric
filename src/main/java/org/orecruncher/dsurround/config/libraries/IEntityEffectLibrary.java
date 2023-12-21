package org.orecruncher.dsurround.config.libraries;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.LivingEntity;
import org.orecruncher.dsurround.effects.entity.EntityEffectInfo;

@Environment(EnvType.CLIENT)
public interface IEntityEffectLibrary extends ILibrary {
    boolean doesEntityEffectInfoExist(LivingEntity entity);
    void clearEntityEffectInfo(LivingEntity entity);
    EntityEffectInfo getEntityEffectInfo(LivingEntity entity);
}
