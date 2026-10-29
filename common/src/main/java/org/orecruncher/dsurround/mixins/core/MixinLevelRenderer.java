package org.orecruncher.dsurround.mixins.core;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.LevelRenderState;
import org.orecruncher.dsurround.effects.WeatherDepth;
import org.orecruncher.dsurround.effects.entity.HealthPopoffs;
import org.orecruncher.dsurround.effects.footprints.Footprints;
import org.orecruncher.dsurround.gui.overlay.lightlevel.LightLevelRenderer;
import org.orecruncher.dsurround.mixinutils.MixinHelpers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The level's passes run as lambdas, whose names differ between Fabric and NeoForge on this version (and from what
 * either runs in production), so nothing here targets one: what the passes do is hooked through the methods that add
 * them, and the methods they call (see also MixinWorldBorderRenderer and MixinChunkSectionsToRender).
 */
@Mixin(LevelRenderer.class)
public class MixinLevelRenderer {

    // The frustum the level is culled by this frame, for the light level numbers
    @Unique
    private Frustum dsurround$cullFrustum;

    @Inject(method = "addMainPass", at = @At("HEAD"))
    private void dsurround$captureFrustum(CallbackInfo ci, @Local(argsOnly = true) Frustum frustum) {
        this.dsurround$cullFrustum = frustum;
    }

    /**
     * The world's entities and block entities have been submitted for drawing this frame; add the footprints, the
     * light level numbers and the health pop-offs. Outside any render pass.
     */
    @Inject(method = "submitBlockEntities", at = @At("TAIL"))
    private void dsurround$submitLightLevels(PoseStack poseStack, LevelRenderState levelRenderState, SubmitNodeStorage collector, CallbackInfo ci) {
        Footprints.submitAll(collector, levelRenderState.cameraRenderState);
        if (this.dsurround$cullFrustum != null)
            LightLevelRenderer.submit(collector, levelRenderState.cameraRenderState, this.dsurround$cullFrustum);
        HealthPopoffs.submit(collector, levelRenderState.cameraRenderState);
    }

    /**
     * The frame's weather pass, where the level's weather is drawn: particle weather replaces vanilla's only there,
     * not where another mod calls the weather renderer itself (as Ars Nouveau did on 1.21.1, into its sky texture).
     * The pass is what the weather pass's frame pass runs, wrapped to mark it.
     */
    @WrapOperation(method = "addWeatherPass*", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/framegraph/FramePass;executes(Ljava/lang/Runnable;)V"), require = 0)
    private void dsurround$markWeatherPass(FramePass pass, Runnable task, Operation<Void> original) {
        original.call(pass, (Runnable) () -> {
            dsurround$enterWeatherPass();
            try {
                task.run();
            } finally {
                MixinHelpers.inLevelWeatherPass = false;
            }
        });
    }

    @Unique
    private static void dsurround$enterWeatherPass() {
        MixinHelpers.inLevelWeatherPass = true;
        // With Fabulous graphics, the scene's depth for what is drawn to the weather's target to be tested against
        MixinHelpers.WEATHER_DEPTH_GUARD.run(() -> WeatherDepth.prepare(MixinHelpers.precipitation().isActive()
                || MixinHelpers.windowFrost().isActive() || MixinHelpers.groundFog().isActive()
                || MixinHelpers.lightning().hasStrikes()));
        // Frost on windows with Fabulous graphics, and the ground fog without, before the weather, so the rain and
        // snow are drawn over them
        MixinHelpers.WINDOW_FROST_GUARD.run(() -> MixinHelpers.windowFrost().renderBeforeWeather());
        MixinHelpers.GROUND_FOG_GUARD.run(() -> MixinHelpers.groundFog().renderBeforeWeather());
    }
}
