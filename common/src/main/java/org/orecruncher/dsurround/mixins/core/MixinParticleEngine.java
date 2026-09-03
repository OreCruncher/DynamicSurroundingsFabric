package org.orecruncher.dsurround.mixins.core;

import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import org.orecruncher.dsurround.effects.particles.DSurroundParticleTypes;
import org.orecruncher.dsurround.effects.particles.SpriteOnlyProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleEngine.class)
public abstract class MixinParticleEngine {

    @Inject(method = "registerProviders()V", at = @At("TAIL"))
    public void dsurround$registerHook(CallbackInfo ci) {
        this.dsurround$register(DSurroundParticleTypes.WATER_RIPPLE, spriteSet -> new SpriteOnlyProvider(DSurroundParticleTypes.WATER_RIPPLE, spriteSet));
        this.dsurround$register(DSurroundParticleTypes.WATER_RIPPLE_PIXELATED, spriteSet -> new SpriteOnlyProvider(DSurroundParticleTypes.WATER_RIPPLE_PIXELATED, spriteSet));
    }

    @Invoker("register")
    public abstract <T extends ParticleOptions> void dsurround$register(ParticleType<T> particleType, ParticleEngine.SpriteParticleRegistration<T> spriteParticleRegistration);
}
