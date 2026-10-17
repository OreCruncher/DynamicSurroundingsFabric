package org.orecruncher.dsurround.mixins.core;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.orecruncher.dsurround.gui.overlay.lightlevel.LightLevelRenderer;
import org.orecruncher.dsurround.mixinutils.MixinHelpers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class MixinLevelRenderer {

    /**
     * The world's entities, block entities and particles have been submitted for drawing this frame; add the light
     * level numbers. Outside any render pass.
     */
    @Inject(method = "submitFeatures(Lnet/minecraft/client/renderer/state/level/LevelRenderState;Lnet/minecraft/client/renderer/SubmitNodeCollector;Z)V", at = @At("TAIL"))
    private void dsurround$submitLightLevels(LevelRenderState levelRenderState, SubmitNodeCollector collector, boolean renderOutline, CallbackInfo ci) {
        LightLevelRenderer.submit(collector, levelRenderState.cameraRenderState);
    }

    /**
     * The frame's weather pass, where the level's weather is drawn: particle weather replaces vanilla's only there,
     * not where another mod calls the weather renderer itself (as Ars Nouveau did on 1.21.1, into its sky texture).
     * The pass runs as a lambda; matched by name, as its arguments differ on NeoForge.
     */
    @Inject(method = "lambda$addWeatherPass$0", at = @At("HEAD"), require = 0)
    private void dsurround$enterWeatherPass(CallbackInfo ci) {
        MixinHelpers.inLevelWeatherPass = true;
        // Frost on windows with Fabulous graphics, and the ground fog without, before the weather, so the rain and
        // snow are drawn over them
        MixinHelpers.WINDOW_FROST_GUARD.run(() -> MixinHelpers.windowFrost().renderBeforeWeather());
        MixinHelpers.GROUND_FOG_GUARD.run(() -> MixinHelpers.groundFog().renderBeforeWeather());
    }

    /**
     * The ground fog with Fabulous graphics, after the weather (see GroundFogHandler.renderAfterWeather) and before the
     * world border, which would otherwise be hidden behind the depth the mist writes.
     */
    @Inject(method = "lambda$addWeatherPass$0", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/WorldBorderRenderer;render(Lnet/minecraft/client/renderer/state/level/WorldBorderRenderState;Lnet/minecraft/world/phys/Vec3;DD)V"), require = 0)
    private void dsurround$groundFogAfterWeather(CallbackInfo ci) {
        MixinHelpers.GROUND_FOG_GUARD.run(() -> MixinHelpers.groundFog().renderAfterWeather());
    }

    /**
     * Frost on windows without Fabulous graphics: in the main pass, the scene's depth copied before the translucent
     * features and blocks, then the frost drawn after the translucent blocks, tested against that copy, so water and
     * stained glass behind a window are under its frost and those in front of it don't hide it (see
     * WindowFrostHandler). The pass runs as a lambda; matched by name, as its arguments differ on NeoForge.
     */
    @Inject(method = "lambda$addMainPass$0", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;executeTranslucent()V"), require = 0)
    private void dsurround$frostDepthBeforeTranslucent(CallbackInfo ci) {
        MixinHelpers.WINDOW_FROST_GUARD.run(() -> MixinHelpers.windowFrost().copyDepthBeforeTranslucent());
    }

    @Inject(method = "lambda$addMainPass$0", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;executeTranslucentAfterTerrain()V"), require = 0)
    private void dsurround$frostAfterTranslucentTerrain(CallbackInfo ci) {
        MixinHelpers.WINDOW_FROST_GUARD.run(() -> MixinHelpers.windowFrost().renderAfterTranslucentTerrain());
    }

    @Inject(method = "lambda$addWeatherPass$0", at = @At("RETURN"), require = 0)
    private void dsurround$leaveWeatherPass(CallbackInfo ci) {
        MixinHelpers.inLevelWeatherPass = false;
    }
}
