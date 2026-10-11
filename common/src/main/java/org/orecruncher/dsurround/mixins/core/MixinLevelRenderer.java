package org.orecruncher.dsurround.mixins.core;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.orecruncher.dsurround.effects.precipitation.StormSounds;
import org.orecruncher.dsurround.eventing.ISkyRender;
import org.orecruncher.dsurround.mixinutils.MixinHelpers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class MixinLevelRenderer {

    /**
     * What is drawn in the sky after it (the aurora), from where the level calls for the sky rather than from inside
     * renderSky: a mod that draws a sky of its own may cancel renderSky at its start (Astral Sorcery does, for its
     * constellations), and nothing at the end of renderSky would run.
     */
    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderSky(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;FLnet/minecraft/client/Camera;ZLjava/lang/Runnable;)V"))
    private void dsurround$renderSky(LevelRenderer renderer, Matrix4f frustumMatrix, Matrix4f projectionMatrix, float partialTick, Camera camera, boolean isFoggy, Runnable skyFogSetup, Operation<Void> original) {
        original.call(renderer, frustumMatrix, projectionMatrix, partialTick, camera, isFoggy, skyFogSetup);
        ISkyRender.EVENT.invoker().onRenderSky(frustumMatrix, partialTick, camera, isFoggy);
    }

    /**
     * The sky's colour, turned toward the dust's in a dust storm, as the fog is. Only where the sky is drawn: the fog
     * reads the sky's colour too, from the level, and is tinted on its own.
     */
    @ModifyExpressionValue(method = "renderSky(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;FLnet/minecraft/client/Camera;ZLjava/lang/Runnable;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getSkyColor(Lnet/minecraft/world/phys/Vec3;F)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 dsurround$tintSkyColor(Vec3 color, @Local(argsOnly = true) float partialTick) {
        return MixinHelpers.fogHandler().tintSkyColor(color, partialTick);
    }

    /**
     * Particle rain and snow, when on, are drawn in place of vanilla's weather. This comes before NeoForge's
     * DimensionSpecialEffects hook, so particle weather is off in a dimension that draws its own (the guess in
     * DimensionInfo, or a dimension rule), or it would replace it.
     */
    @Inject(method = "renderSnowAndRain(Lnet/minecraft/client/renderer/LightTexture;FDDD)V", at = @At("HEAD"), cancellable = true)
    private void dsurround$renderPrecipitation(LightTexture lightTexture, float partialTick, double camX, double camY, double camZ, CallbackInfo ci) {
        if (MixinHelpers.precipitation().render(lightTexture, partialTick))
            ci.cancel();
    }

    /**
     * While particle rain is drawn, its drops splash where they land, so vanilla's splashes, scattered at random
     * around the player, are left out. Vanilla's rain sound, played from the same method, is kept.
     */
    @WrapWithCondition(method = "tickRain(Lnet/minecraft/client/Camera;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"))
    private boolean dsurround$vanillaRainSplash(ClientLevel level, ParticleOptions particle, double x, double y, double z, double xd, double yd, double zd) {
        return !MixinHelpers.precipitation().isActive();
    }

    /**
     * The rain sound, as 1.12.2 had it: the mod's calm rain, louder as the storm strengthens, with vanilla's heavier
     * rain blended in from a moderate storm up. Both calls, the rain in the open and on a roof above, come through
     * here; each keeps vanilla's volume and pitch for where it is, scaled.
     */
    @WrapOperation(method = "tickRain(Lnet/minecraft/client/Camera;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;playLocalSound(Lnet/minecraft/core/BlockPos;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZ)V"))
    private void dsurround$rainSound(ClientLevel level, BlockPos pos, SoundEvent sound, SoundSource source, float volume, float pitch, boolean distanceDelay, Operation<Void> original) {
        if (!MixinHelpers.weatherOptions.enableStormSounds) {
            original.call(level, pos, sound, source, volume, pitch, distanceDelay);
            return;
        }
        var rainLevel = MixinHelpers.weatherSimulation().rainLevel(level.getRainLevel(1F));
        var calm = MixinHelpers.SOUND_LIBRARY.getSound(StormSounds.RAIN);
        level.playLocalSound(pos, calm, source, volume * StormSounds.calmShare(rainLevel), pitch, distanceDelay);
        var heavy = StormSounds.heavyShare(rainLevel);
        if (heavy > 0F)
            original.call(level, pos, sound, source, volume * heavy, pitch, distanceDelay);
    }

    /**
     * The storm's strength in place of the world's rain level where the rain is drawn (how opaque the rain and snow
     * are) and ticked (how many splashes, and the rain sound). The world's level itself is left alone, so the sky,
     * isRaining() and everything else reading it are unchanged. NeoForge's DimensionSpecialEffects hooks come first
     * in both methods; a dimension that draws its own weather is left to it.
     */
    @ModifyExpressionValue(method = {
            "renderSnowAndRain(Lnet/minecraft/client/renderer/LightTexture;FDDD)V",
            "tickRain(Lnet/minecraft/client/Camera;)V"
    }, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getRainLevel(F)F"))
    private float dsurround$stormRainLevel(float vanillaLevel) {
        return MixinHelpers.weatherSimulation().rainLevel(vanillaLevel);
    }
}
