package org.orecruncher.dsurround.mixins.core;

import net.minecraft.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.effects.entity.EntityEffectInfo;
import org.orecruncher.dsurround.xface.ILivingEntityExtended;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(LivingEntity.class)
public class MixinLivingEntity implements ILivingEntityExtended {

    private EntityEffectInfo dsurround_effectInfo;

    @Override
    public EntityEffectInfo getEffectInfo() {
        return this.dsurround_effectInfo;
    }

    @Override
    public void setEffectInfo(@Nullable EntityEffectInfo info) {
        this.dsurround_effectInfo = info;
    }
}
