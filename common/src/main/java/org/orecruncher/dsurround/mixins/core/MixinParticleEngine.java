package org.orecruncher.dsurround.mixins.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import org.orecruncher.dsurround.mixinutils.IWindBlown;
import org.orecruncher.dsurround.mixinutils.MixinHelpers;
import org.orecruncher.dsurround.runtime.weather.Shelter;
import org.orecruncher.dsurround.tags.ParticleTags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleEngine.class)
public abstract class MixinParticleEngine {

    @Shadow
    protected ClientLevel level;

    /**
     * Marks the particles the wind carries, as they are made: this is the only place a particle's type is known.
     * Particles handed straight to {@code add} (the mod's own, block breaking) have none, so are never marked. Nor are
     * particles another mod makes off the client thread: Shelter looks at the world with scratch state of its own,
     * client thread only.
     */
    @Inject(method = "makeParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)Lnet/minecraft/client/particle/Particle;", at = @At("RETURN"), require = 0)
    private void dsurround$markWindBlown(ParticleOptions options, double x, double y, double z, double dx, double dy, double dz, CallbackInfoReturnable<Particle> cir) {
        var particle = cir.getReturnValue();
        if (particle == null || !MixinHelpers.particleTweaksConfig.windBlowsParticles || !Minecraft.getInstance().isSameThread())
            return;
        try {
            if (!MixinHelpers.TAG_LIBRARY.is(ParticleTags.WIND_BLOWN, options.getType()))
                return;
            // Sheltered where it was made: indoors or in a cave, it stays as it is
            var exposure = Shelter.exposure(this.level, BlockPos.containing(x, y, z));
            if (exposure > 0F)
                ((IWindBlown) particle).dsurround$blowInWind(exposure);
        } catch (Throwable t) {
            MixinHelpers.WIND_BLOWN_GUARD.failed(t);
        }
    }
}
