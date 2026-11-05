// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.mixins.core;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import org.orecruncher.dsurround.mixinutils.MixinHelpers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WeatherEffectRenderer.class)
public class MixinWeatherEffectRenderer {

    /**
     * The storm's strength in place of the world's rain level where the rain and snow are set up for drawing (how
     * opaque they are). The world's level itself is left alone, so the sky, isRaining() and everything else reading it
     * are unchanged. Ticked rain is in MixinClientLevel. A dimension that draws its own weather (NeoForge) is left to
     * it before this is read.
     */
    @ModifyExpressionValue(method = "extractRenderState(Lnet/minecraft/client/multiplayer/ClientLevel;FLnet/minecraft/world/phys/Vec3;Lnet/minecraft/client/renderer/state/level/WeatherRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getRainLevel(F)F"), require = 0)
    private float dsurround$stormRainLevelDrawn(float vanillaLevel) {
        return MixinHelpers.stormRainLevel(vanillaLevel);
    }

    /**
     * Vanilla's weather left out for particle rain and snow, in the level's main pass only: another mod drawing the
     * weather itself gets vanilla's (see MixinLevelRenderer). Vanilla's is drawn if ours won't be (turned off, a shader
     * pack, a failure). Ours is drawn later, once this pass is closed (see PrecipitationHandler).
     */
    @Inject(method = {
            "render(Lnet/minecraft/client/renderer/state/level/WeatherRenderState;Lcom/mojang/renderpearl/api/commands/RenderPass;)V",
            "renderOit(Lnet/minecraft/client/renderer/oit/OitStage;Lnet/minecraft/client/renderer/state/level/WeatherRenderState;Lcom/mojang/renderpearl/api/commands/RenderPass;)V"
    }, at = @At("HEAD"), cancellable = true, require = 0)
    private void dsurround$renderPrecipitation(CallbackInfo ci) {
        if (MixinHelpers.inLevelMainPass && MixinHelpers.PRECIPITATION_GUARD.get(() -> MixinHelpers.precipitation().replacesWeather(), false))
            ci.cancel();
    }
}
