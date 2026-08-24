package org.orecruncher.dsurround.mixins.core;

import net.minecraft.world.entity.LivingEntity;
import org.orecruncher.dsurround.mixinutils.ILivingEntityExtended;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(LivingEntity.class)
public class MixinLivingEntity implements ILivingEntityExtended {

    @Shadow
    protected boolean jumping;

    @Override
    public boolean dsurround_isJumping() {
        return this.jumping;
    }
}
