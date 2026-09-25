package org.orecruncher.dsurround.mixins.core;

import net.minecraft.client.particle.ParticleResources;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import org.orecruncher.dsurround.effects.particles.DSurroundParticleTypes;
import org.orecruncher.dsurround.effects.particles.SpriteOnlyProvider;
import org.orecruncher.dsurround.lib.random.Randomizer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleResources.class)
public abstract class MixinParticleResources {

    @Inject(method = "registerProviders()V", at = @At("TAIL"))
    public void dsurround$registerHook(CallbackInfo ci) {
        this.dsurround$register(DSurroundParticleTypes.WATER_RIPPLE, spriteSet -> new SpriteOnlyProvider(DSurroundParticleTypes.WATER_RIPPLE, spriteSet, Randomizer.current()));
        this.dsurround$register(DSurroundParticleTypes.WATER_RIPPLE_PIXELATED, spriteSet -> new SpriteOnlyProvider(DSurroundParticleTypes.WATER_RIPPLE_PIXELATED, spriteSet, Randomizer.current()));
        this.dsurround$register(DSurroundParticleTypes.WATERFALL_CASCADE, spriteSet -> new SpriteOnlyProvider(DSurroundParticleTypes.WATERFALL_CASCADE, spriteSet, Randomizer.current()));
    }

    @Invoker("register")
    public abstract <T extends ParticleOptions> void dsurround$register(ParticleType<T> particleType, ParticleResources.SpriteParticleRegistration<T> spriteParticleRegistration);

}
