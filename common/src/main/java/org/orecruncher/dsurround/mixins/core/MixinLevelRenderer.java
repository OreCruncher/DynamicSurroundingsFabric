// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.mixins.core;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.orecruncher.dsurround.effects.entity.HealthPopoffs;
import org.orecruncher.dsurround.effects.footprints.Footprints;
import org.orecruncher.dsurround.eventing.IWorldRender;
import org.orecruncher.dsurround.gui.overlay.lightlevel.LightLevelRenderer;
import org.orecruncher.dsurround.mixinutils.MixinHelpers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class MixinLevelRenderer {

    /**
     * The world's entities, block entities and particles have been submitted for drawing this frame; add the
     * footprints, the light level numbers and the health pop-offs, and with a shader pack the lightning. Outside any
     * render pass.
     */
    @Inject(method = "submitFeatures(Lnet/minecraft/client/renderer/state/level/LevelRenderState;Lnet/minecraft/client/renderer/SubmitNodeCollector;Z)V", at = @At("TAIL"))
    private void dsurround$submitLightLevels(LevelRenderState levelRenderState, SubmitNodeCollector collector, boolean renderOutline, CallbackInfo ci) {
        Footprints.submitAll(collector, levelRenderState.cameraRenderState);
        LightLevelRenderer.submit(collector, levelRenderState.cameraRenderState);
        HealthPopoffs.submit(collector, levelRenderState.cameraRenderState);
        MixinHelpers.LIGHTNING_GUARD.run(() -> MixinHelpers.lightning().submitForShaderPack(collector, levelRenderState.cameraRenderState));
    }

    /**
     * The level's main pass, where its weather is drawn: particle weather replaces vanilla's only here, not where another
     * mod calls the weather renderer itself (as Ars Nouveau did on 1.21.1, into its sky texture). The pass runs as a
     * lambda; matched by name, as its arguments differ on NeoForge.
     */
    @Inject(method = "lambda$addMainPass$0", at = @At("HEAD"), require = 0)
    private void dsurround$enterMainPass(CallbackInfo ci) {
        MixinHelpers.inLevelMainPass = true;
    }

    /**
     * The translucent things are drawn and their render pass is closed: everything the mod draws over the world (see
     * IWorldRender). Before the outlines, as these are the world's.
     */
    @Inject(method = "lambda$addMainPass$0", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;executeOutline(Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;)V"), require = 0)
    private void dsurround$renderWorld(CallbackInfo ci) {
        IWorldRender.EVENT.invoker().onRenderWorld();
    }

    @Inject(method = "lambda$addMainPass$0", at = @At("RETURN"), require = 0)
    private void dsurround$leaveMainPass(CallbackInfo ci) {
        MixinHelpers.inLevelMainPass = false;
    }
}
