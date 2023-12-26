package org.orecruncher.dsurround.mixins.core;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.TrackedData;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.effects.entity.EntityEffectInfo;
import org.orecruncher.dsurround.xface.ILivingEntityExtended;
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
    private static TrackedData<Integer> POTION_SWIRLS_COLOR;

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
        return entity.getDataTracker().get(POTION_SWIRLS_COLOR);
    }

    @Override
    public void dsurround_setPotionSwirlColor(int color) {
        var entity =  ((LivingEntity)((Object)this));
        entity.getDataTracker().set(POTION_SWIRLS_COLOR, color);
    }
}
