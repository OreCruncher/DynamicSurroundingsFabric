package org.orecruncher.dsurround.mixins.core;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import org.orecruncher.dsurround.mixinutils.MixinHelpers;
import org.orecruncher.dsurround.runtime.weather.StormSounds;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WeatherEffectRenderer.class)
public class MixinWeatherEffectRenderer {

    /**
     * The storm's strength in place of the world's rain level where the rain and snow are set up for drawing (how
     * opaque they are). The world's level itself is left alone, so the sky, isRaining() and everything else reading it
     * are unchanged. Ticked rain is below. A dimension that draws its own weather (NeoForge) is left to
     * it before this is read.
     */
    @ModifyExpressionValue(method = "extractRenderState(Lnet/minecraft/world/level/Level;IFLnet/minecraft/world/phys/Vec3;Lnet/minecraft/client/renderer/state/WeatherRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getRainLevel(F)F"), require = 0)
    private float dsurround$stormRainLevelDrawn(float vanillaLevel) {
        return MixinHelpers.stormRainLevel(vanillaLevel);
    }

    /**
     * Particle rain and snow in place of vanilla's, in the frame's weather pass only: another mod drawing the weather
     * itself gets vanilla's (see MixinLevelRenderer). Vanilla's is drawn if ours isn't (turned off, a shader pack, a
     * failure).
     * <p>
     * Both overloads are named: vanilla has the first, which the weather pass calls; NeoForge adds the second, taking
     * the level's render state. A name alone matches only one overload.
     */
    @Inject(method = {
            "render(Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/client/renderer/state/WeatherRenderState;)V",
            "render(Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/client/renderer/state/WeatherRenderState;Lnet/minecraft/client/renderer/state/LevelRenderState;)V"
    }, at = @At("HEAD"), cancellable = true, require = 0)
    private void dsurround$renderPrecipitation(CallbackInfo ci) {
        if (MixinHelpers.inLevelWeatherPass && MixinHelpers.PRECIPITATION_GUARD.get(() -> MixinHelpers.precipitation().render(), false))
            ci.cancel();
    }

    /**
     * The storm's strength in place of the world's rain level where the rain is ticked: how many splashes, and the
     * rain sound.
     */
    @ModifyExpressionValue(method = "tickRainParticles", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getRainLevel(F)F"), require = 0)
    private float dsurround$stormRainLevelTicked(float vanillaLevel) {
        return MixinHelpers.stormRainLevel(vanillaLevel);
    }

    /**
     * While particle rain is drawn, its drops splash where they land, so vanilla's splashes, scattered at random
     * around the player, are left out. Vanilla's rain sound, played from the same method, is kept.
     */
    @WrapWithCondition(method = "tickRainParticles", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"), require = 0)
    private boolean dsurround$vanillaRainSplash(ClientLevel level, ParticleOptions particle, double x, double y, double z, double xd, double yd, double zd) {
        return MixinHelpers.RAIN_SPLASH_GUARD.get(() -> !MixinHelpers.precipitation().isActive(), true);
    }

    /**
     * The rain sound, as 1.12.2 had it: the mod's calm rain, louder as the storm strengthens, with vanilla's heavier
     * rain blended in from a moderate storm up. Both calls, the rain in the open and on a roof above, come through
     * here; each keeps vanilla's volume and pitch for where it is, scaled.
     */
    @WrapOperation(method = "tickRainParticles", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;playLocalSound(Lnet/minecraft/core/BlockPos;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZ)V"), require = 0)
    private void dsurround$rainSound(ClientLevel level, BlockPos pos, SoundEvent sound, SoundSource source, float volume, float pitch, boolean distanceDelay, Operation<Void> original) {
        if (!MixinHelpers.weatherOptions.enableStormSounds) {
            original.call(level, pos, sound, source, volume, pitch, distanceDelay);
            return;
        }
        // The shares are worked out before anything plays, so a failure leaves vanilla's sound, played once
        float calmVolume;
        float heavy;
        SoundEvent calm;
        try {
            var rainLevel = MixinHelpers.weatherSimulation().rainLevel(level.getRainLevel(1F));
            calm = MixinHelpers.SOUND_LIBRARY.getSound(StormSounds.RAIN);
            calmVolume = volume * StormSounds.calmShare(rainLevel);
            heavy = StormSounds.heavyShare(rainLevel);
        } catch (Throwable t) {
            MixinHelpers.RAIN_SOUND_GUARD.failed(t);
            original.call(level, pos, sound, source, volume, pitch, distanceDelay);
            return;
        }
        level.playLocalSound(pos, calm, source, calmVolume, pitch, distanceDelay);
        if (heavy > 0F)
            original.call(level, pos, sound, source, volume * heavy, pitch, distanceDelay);
    }
}
