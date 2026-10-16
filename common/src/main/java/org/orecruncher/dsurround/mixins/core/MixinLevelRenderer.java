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
    }

    @Inject(method = "lambda$addWeatherPass$0", at = @At("RETURN"), require = 0)
    private void dsurround$leaveWeatherPass(CallbackInfo ci) {
        MixinHelpers.inLevelWeatherPass = false;
    }
}
