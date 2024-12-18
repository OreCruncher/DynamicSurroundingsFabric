package org.orecruncher.dsurround.mixins.core;

import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.effects.entity.EntityEffectInfo;
import org.orecruncher.dsurround.mixinutils.ILivingEntityExtended;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LivingEntity.class)
public class MixinLivingEntity implements ILivingEntityExtended {

    @Unique
    private EntityEffectInfo dsurround_effectInfo;

    @Shadow
    protected boolean jumping;

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
}
