package org.orecruncher.dsurround.mixins.core;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.RainSplashParticle;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.util.math.Vec3d;
import org.orecruncher.dsurround.effects.WaterRippleHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(RainSplashParticle.Factory.class)
public class MixinRainSplashParticle {

    @Inject(method = "createParticle(Lnet/minecraft/particle/DefaultParticleType;Lnet/minecraft/client/world/ClientWorld;DDDDDD)Lnet/minecraft/client/particle/Particle;", at = @At("RETURN"), locals = LocalCapture.CAPTURE_FAILSOFT, cancellable = true)
    public void dsurround_makeParticle(DefaultParticleType defaultParticleType, ClientWorld clientWorld, double x, double y, double z, double g, double h, double i, CallbackInfoReturnable<Particle> cir) {
        WaterRippleHandler.createRippleParticle(clientWorld, cir.getReturnValue(), new Vec3d(x, y, z));
    }
}