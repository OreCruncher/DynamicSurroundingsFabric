package org.orecruncher.dsurround.mixins.core;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.orecruncher.dsurround.eventing.ISkyRender;
import org.orecruncher.dsurround.mixinutils.MixinHelpers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class MixinLevelRenderer {

    @Inject(method = "renderSky(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;FLnet/minecraft/client/Camera;ZLjava/lang/Runnable;)V", at = @At("RETURN"))
    private void dsurround$renderSky(Matrix4f frustumMatrix, Matrix4f projectionMatrix, float partialTick, Camera camera, boolean isFoggy, Runnable skyFogSetup, CallbackInfo ci) {
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
     * DimensionSpecialEffects hook, so it also replaces a dimension's own weather drawing.
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
