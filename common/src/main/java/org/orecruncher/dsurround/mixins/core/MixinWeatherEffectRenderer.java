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

    // Ticked rain (its level, splashes and sound) is hooked where tickRainParticles is called and on what it calls,
    // not inside it: see MixinHelpers.rainTickThread
}
