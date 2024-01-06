package org.orecruncher.dsurround.mixins.core;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.effects.entity.EntityEffectInfo;
import org.orecruncher.dsurround.mixinutils.ILivingEntityExtended;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LivingEntity.class)
public class MixinLivingEntity implements ILivingEntityExtended {

    @Unique
    private EntityEffectInfo dsurround_effectInfo;

    @Shadow
    protected boolean jumping;

    @Final
    @Shadow
    private static EntityDataAccessor<Integer> DATA_EFFECT_COLOR_ID;

    @Override
    public EntityEffectInfo dsurround_getEffectInfo() {
        return this.dsurround_effectInfo;
    }

    @Override
    public void dsurround_setEffectInfo(@Nullable EntityEffectInfo info) {
        this.dsurround_effectInfo = info;
    }

    @Override
    public boolean dsurround_isJumping() {
        return this.jumping;
    }

    @Override
    public int dsurround_getPotionSwirlColor() {
        var entity =  ((LivingEntity)((Object)this));
        return entity.getEntityData().get(DATA_EFFECT_COLOR_ID);
    }

    @Override
    public void dsurround_setPotionSwirlColor(int color) {
        var entity =  ((LivingEntity)((Object)this));
        entity.getEntityData().set(DATA_EFFECT_COLOR_ID, color);
    }
}
