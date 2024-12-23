package org.orecruncher.dsurround.mixins.core;

import dev.architectury.platform.Platform;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.WaterDropParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.phys.Vec3;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.effects.WaterRippleHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WaterDropParticle.Provider.class)
public class MixinRainSplashParticle {

    @Inject(method = "createParticle(Lnet/minecraft/core/particles/SimpleParticleType;Lnet/minecraft/client/multiplayer/ClientLevel;DDDDDD)Lnet/minecraft/client/particle/Particle;", at = @At("RETURN"))
    public void dsurround_makeParticle(SimpleParticleType defaultParticleType, ClientLevel clientWorld, double x, double y, double z, double g, double h, double i, CallbackInfoReturnable<Particle> cir) {
        // Do not want to do rain splashes if particle rain is installed
        if (!Platform.isModLoaded(Constants.MOD_PARTICLE_RAIN))
            WaterRippleHandler.createRippleParticle(clientWorld, cir.getReturnValue(), new Vec3(x, y, z));
    }
}