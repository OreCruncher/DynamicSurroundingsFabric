package org.orecruncher.dsurround.mixins.core;

import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Particle.class)
public interface MixinParticle {

    @Accessor("velocityX")
    double getVelocityX();

    @Accessor("velocityY")
    double getVelocityY();

    @Accessor("velocityZ")
    double getVelocityZ();
}
